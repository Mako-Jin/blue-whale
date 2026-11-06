package com.bluewhale.party.analytics.application.service.impl;

import com.bluewhale.party.activity.application.service.ActivityAppService;
import com.bluewhale.party.activity.application.view.ActivityMetricView;
import com.bluewhale.party.analytics.application.service.AnalyticsAppService;
import com.bluewhale.party.analytics.application.view.MetricItemView;
import com.bluewhale.party.analytics.application.view.MetricOverviewView;
import com.bluewhale.party.analytics.application.view.MetricTrendPointView;
import com.bluewhale.party.analytics.domain.model.MetricScope;
import com.bluewhale.party.analytics.domain.model.MetricSnapshot;
import com.bluewhale.party.analytics.domain.model.MetricType;
import com.bluewhale.party.analytics.domain.repository.MetricSnapshotRepository;
import com.bluewhale.party.branch.application.service.BranchAppService;
import com.bluewhale.party.branch.application.view.BranchScopeView;
import com.bluewhale.party.branch.application.view.BranchTermDueView;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.config.PartyProperties;
import com.bluewhale.party.fee.application.service.FeeAppService;
import com.bluewhale.party.fee.application.view.FeeMetricView;
import com.bluewhale.party.integration.application.service.EmployeeSnapshotAppService;
import com.bluewhale.party.member.application.service.MemberAppService;
import com.bluewhale.party.member.application.view.MemberMetricView;
import com.bluewhale.party.union.application.service.UnionActivityAppService;
import com.bluewhale.party.union.application.service.UnionMemberAppService;
import com.bluewhale.party.union.application.service.UnionProposalAppService;
import com.bluewhale.party.union.application.view.UnionMemberMetricView;
import com.bluewhale.party.union.application.view.UnionProposalMetricView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 看板与统计应用服务实现。
 *
 * <p>三个硬口径：</p>
 * <ol>
 *   <li><b>比率不落库</b>：快照只存分子分母，展示时按当前规则计算；</li>
 *   <li><b>覆盖写不追加</b>：同一天重刷走唯一键 upsert，趋势图不会一天多点；</li>
 *   <li><b>导出只出聚合数</b>：CSV 列只有日期、范围、指标、分子、分母与比率，
 *       不含姓名、身份证、手机号，脱敏不依赖导出字段裁剪。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsAppServiceImpl implements AnalyticsAppService {

    /** 月份格式 */
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    /** 全租户范围名称 */
    private static final String ALL_SCOPE_NAME = "全租户";

    /** 导出 CSV 表头 */
    private static final String CSV_HEADER = "统计日,统计范围,指标编码,指标名称,分子,分母,比率(%)";

    private final MetricSnapshotRepository metricSnapshotRepository;

    private final MemberAppService memberAppService;

    private final FeeAppService feeAppService;

    private final ActivityAppService activityAppService;

    private final BranchAppService branchAppService;

    private final UnionMemberAppService unionMemberAppService;

    private final UnionActivityAppService unionActivityAppService;

    private final UnionProposalAppService unionProposalAppService;

    private final EmployeeSnapshotAppService employeeSnapshotAppService;

    private final PartyProperties properties;

    @Override
    public MetricOverviewView partyOverview(LocalDate statDate, Long branchId, PartyActor actor) {
        assertGate();
        LocalDate date = statDate == null ? LocalDate.now() : statDate;
        Scope scope = resolvePartyScope(branchId, actor);
        List<MetricSnapshot> stored = metricSnapshotRepository.listByDate(date, scope.type, scope.id);
        MetricOverviewView view = new MetricOverviewView();
        view.setStatDate(date);
        view.setScopeType(scope.type.name());
        view.setScopeId(scope.id);
        view.setScopeName(scope.name);
        view.setLive(stored.isEmpty());
        List<MetricSnapshot> snapshots = stored.isEmpty()
                ? buildPartySnapshots(date, scope) : stored;
        view.setItems(items(snapshots));
        view.setTrend(metricSnapshotRepository.listTrend(date.minusDays(properties.getMetricTrendDays() - 1L),
                        date, MetricType.FEE_COLLECTION_AMOUNT.name(), scope.type, scope.id)
                .stream().map(MetricTrendPointView::from).toList());
        return view;
    }

    @Override
    public List<MetricItemView> refreshParty(LocalDate statDate, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        LocalDate date = statDate == null ? LocalDate.now() : statDate;
        Scope all = new Scope(MetricScope.ALL, MetricSnapshot.ALL_SCOPE_ID, ALL_SCOPE_NAME);
        List<MetricSnapshot> snapshots = new ArrayList<>(buildPartySnapshots(date, all));
        for (BranchScopeView branch : branchAppService.activeScopes()) {
            snapshots.addAll(buildPartySnapshots(date, new Scope(MetricScope.BRANCH,
                    branch.getBranchId(), branch.getBranchName())));
        }
        metricSnapshotRepository.saveAll(snapshots);
        return items(snapshots.stream()
                .filter(snapshot -> MetricScope.ALL.name().equals(snapshot.getScopeType()))
                .toList());
    }

    @Override
    public String exportPartyCsv(LocalDate from, LocalDate to, Long branchId, PartyActor actor) {
        assertGate();
        LocalDate end = to == null ? (from == null ? LocalDate.now() : from) : to;
        LocalDate start = from == null ? end : from;
        assertRange(start, end);
        Scope scope = resolvePartyScope(branchId, actor);
        List<MetricSnapshot> snapshots = metricSnapshotRepository.listBetween(start, end, scope.type, scope.id);
        if (snapshots.isEmpty() && start.equals(end)) {
            snapshots = buildPartySnapshots(start, scope);
        }
        return toCsv(snapshots);
    }

    @Override
    public MetricOverviewView unionOverview(LocalDate statDate, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        LocalDate date = statDate == null ? LocalDate.now() : statDate;
        Scope scope = new Scope(MetricScope.ALL, MetricSnapshot.ALL_SCOPE_ID, ALL_SCOPE_NAME);
        List<MetricSnapshot> stored = metricSnapshotRepository.listByDate(date, scope.type, scope.id)
                .stream().filter(snapshot -> isUnionMetric(snapshot.getMetricType())).toList();
        MetricOverviewView view = new MetricOverviewView();
        view.setStatDate(date);
        view.setScopeType(scope.type.name());
        view.setScopeId(scope.id);
        view.setScopeName(scope.name);
        view.setLive(stored.isEmpty());
        List<MetricSnapshot> snapshots = stored.isEmpty()
                ? buildUnionSnapshots(date) : stored;
        view.setItems(items(snapshots));
        view.setTrend(metricSnapshotRepository.listTrend(date.minusDays(properties.getMetricTrendDays() - 1L),
                        date, MetricType.UNION_COVERAGE.name(), scope.type, scope.id)
                .stream().map(MetricTrendPointView::from).toList());
        return view;
    }

    @Override
    public List<MetricItemView> refreshUnion(LocalDate statDate, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        LocalDate date = statDate == null ? LocalDate.now() : statDate;
        List<MetricSnapshot> snapshots = buildUnionSnapshots(date);
        metricSnapshotRepository.saveAll(snapshots);
        return items(snapshots);
    }

    @Override
    public String exportUnionCsv(LocalDate from, LocalDate to, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        LocalDate end = to == null ? (from == null ? LocalDate.now() : from) : to;
        LocalDate start = from == null ? end : from;
        assertRange(start, end);
        List<MetricSnapshot> snapshots = metricSnapshotRepository
                .listBetween(start, end, MetricScope.ALL, MetricSnapshot.ALL_SCOPE_ID)
                .stream().filter(snapshot -> isUnionMetric(snapshot.getMetricType())).toList();
        if (snapshots.isEmpty() && start.equals(end)) {
            snapshots = buildUnionSnapshots(start);
        }
        return toCsv(snapshots);
    }

    /**
     * 党务指标快照（固定顺序，便于看板稳定排序）。
     *
     * @param date 统计日
     * @param scope 范围
     * @return 快照列表
     */
    private List<MetricSnapshot> buildPartySnapshots(LocalDate date, Scope scope) {
        Long branchId = scope.type == MetricScope.BRANCH ? scope.id : null;
        List<MetricSnapshot> snapshots = new ArrayList<>();
        MemberMetricView member = memberAppService.metrics(date, branchId);
        snapshots.add(snapshot(date, scope, MetricType.MEMBER_TOTAL, member.getTotal(), 0));
        member.getAgeBuckets().forEach((bucket, count) ->
                snapshots.add(snapshot(date, scope, ageMetric(bucket), count, 0)));
        member.getPartyAgeBuckets().forEach((bucket, count) ->
                snapshots.add(snapshot(date, scope, partyAgeMetric(bucket), count, 0)));
        member.getEducationBuckets().forEach((bucket, count) ->
                snapshots.add(snapshot(date, scope, educationMetric(bucket), count, 0)));
        snapshots.add(snapshot(date, scope, MetricType.PROBATION_DUE, member.getProbationDue(), 0));

        String periodMonth = MONTH.format(date);
        FeeMetricView fee = feeAppService.metrics(periodMonth, branchId);
        snapshots.add(ratioSnapshot(date, scope, MetricType.FEE_COLLECTION_AMOUNT,
                fee.getPaidAmount(), fee.getDueAmount()));
        snapshots.add(ratioSnapshot(date, scope, MetricType.FEE_COLLECTION_BILL,
                BigDecimal.valueOf(fee.getPaidBillCount()), BigDecimal.valueOf(fee.getDueBillCount())));

        ActivityMetricView activity = activityAppService.metrics(date, branchId);
        snapshots.add(ratioSnapshot(date, scope, MetricType.ACTIVITY_HOLD,
                BigDecimal.valueOf(activity.getHeldCount()), BigDecimal.valueOf(activity.getPlannedCount())));
        snapshots.add(ratioSnapshot(date, scope, MetricType.ACTIVITY_ATTENDANCE,
                BigDecimal.valueOf(activity.getPresentCount()), BigDecimal.valueOf(activity.getSigninCount())));

        int dueBranches;
        int totalBranches;
        if (scope.type == MetricScope.ALL) {
            List<BranchTermDueView> due = branchAppService.termDue(null, null);
            totalBranches = branchAppService.activeScopes().size();
            dueBranches = (int) due.stream().filter(item -> item.getRemainingDays() <= 0).count();
        } else {
            totalBranches = 1;
            dueBranches = branchAppService.termDue(null, null).stream()
                    .anyMatch(item -> scope.id.equals(item.getBranchId()) && item.getRemainingDays() <= 0)
                    ? 1 : 0;
        }
        snapshots.add(ratioSnapshot(date, scope, MetricType.BRANCH_TERM_DUE,
                BigDecimal.valueOf(dueBranches), BigDecimal.valueOf(totalBranches)));
        return snapshots;
    }

    /**
     * 工会指标快照（全租户口径）。
     *
     * @param date 统计日
     * @return 快照列表
     */
    private List<MetricSnapshot> buildUnionSnapshots(LocalDate date) {
        Scope scope = new Scope(MetricScope.ALL, MetricSnapshot.ALL_SCOPE_ID, ALL_SCOPE_NAME);
        List<MetricSnapshot> snapshots = new ArrayList<>();
        UnionMemberMetricView member = unionMemberAppService.metrics();
        long employees = employeeSnapshotAppService.activeEmployeeCount();
        snapshots.add(ratioSnapshot(date, scope, MetricType.UNION_COVERAGE,
                BigDecimal.valueOf(member.getActiveMembers()), BigDecimal.valueOf(employees)));
        LocalDate yearStart = date.withDayOfYear(1);
        long participants = unionActivityAppService.distinctParticipants(yearStart, yearStart.plusYears(1));
        snapshots.add(ratioSnapshot(date, scope, MetricType.UNION_ACTIVITY_PARTICIPATION,
                BigDecimal.valueOf(participants), BigDecimal.valueOf(member.getActiveMembers())));
        UnionProposalMetricView proposal = unionProposalAppService.metrics(date);
        snapshots.add(ratioSnapshot(date, scope, MetricType.PROPOSAL_CLOSED,
                BigDecimal.valueOf(proposal.getClosedCount()), BigDecimal.valueOf(proposal.getTotalCount())));
        return snapshots;
    }

    /**
     * 纯计数指标。
     *
     * @param date 统计日
     * @param scope 范围
     * @param type 指标类型
     * @param numerator 数值
     * @param denominator 分母
     * @return 快照
     */
    private MetricSnapshot snapshot(LocalDate date, Scope scope, MetricType type,
                                    long numerator, long denominator) {
        return MetricSnapshot.of(date, type.name(), scope.type, scope.id, scope.name,
                BigDecimal.valueOf(numerator), BigDecimal.valueOf(denominator));
    }

    /**
     * 比率指标（分子分母可为小数）。
     *
     * @param date 统计日
     * @param scope 范围
     * @param type 指标类型
     * @param numerator 分子
     * @param denominator 分母
     * @return 快照
     */
    private MetricSnapshot ratioSnapshot(LocalDate date, Scope scope, MetricType type,
                                         BigDecimal numerator, BigDecimal denominator) {
        return MetricSnapshot.of(date, type.name(), scope.type, scope.id, scope.name,
                numerator, denominator);
    }

    /**
     * 年龄桶 → 指标编码。
     *
     * @param bucket 桶键
     * @return 指标类型
     */
    private MetricType ageMetric(String bucket) {
        return switch (bucket) {
            case "UNDER_30" -> MetricType.MEMBER_AGE_UNDER_30;
            case "30_39" -> MetricType.MEMBER_AGE_30_39;
            case "40_49" -> MetricType.MEMBER_AGE_40_49;
            case "50_59" -> MetricType.MEMBER_AGE_50_59;
            case "60_PLUS" -> MetricType.MEMBER_AGE_60_PLUS;
            default -> MetricType.MEMBER_AGE_UNKNOWN;
        };
    }

    /**
     * 党龄桶 → 指标类型。
     *
     * @param bucket 桶键
     * @return 指标类型
     */
    private MetricType partyAgeMetric(String bucket) {
        return switch (bucket) {
            case "UNDER_5" -> MetricType.PARTY_AGE_UNDER_5;
            case "5_10" -> MetricType.PARTY_AGE_5_10;
            case "11_20" -> MetricType.PARTY_AGE_11_20;
            case "21_30" -> MetricType.PARTY_AGE_21_30;
            case "31_PLUS" -> MetricType.PARTY_AGE_31_PLUS;
            default -> MetricType.PARTY_AGE_UNKNOWN;
        };
    }

    /**
     * 学历桶 → 指标类型。
     *
     * @param bucket 桶键
     * @return 指标类型
     */
    private MetricType educationMetric(String bucket) {
        MetricType parsed = MetricType.parse("EDUCATION_" + bucket);
        return parsed == null ? MetricType.EDUCATION_UNKNOWN : parsed;
    }

    /**
     * 是否工会指标。
     *
     * @param metricType 指标编码
     * @return 工会指标返回 true
     */
    private boolean isUnionMetric(String metricType) {
        return metricType != null && (metricType.startsWith("UNION_") || "PROPOSAL_CLOSED".equals(metricType));
    }

    /**
     * 快照列表转视图并按指标枚举顺序排序。
     *
     * @param snapshots 快照列表
     * @return 指标视图
     */
    private List<MetricItemView> items(List<MetricSnapshot> snapshots) {
        Map<String, Integer> order = new LinkedHashMap<>();
        for (MetricType type : MetricType.values()) {
            order.put(type.name(), order.size());
        }
        return snapshots.stream()
                .sorted(Comparator.comparingInt(item -> order.getOrDefault(item.getMetricType(), Integer.MAX_VALUE)))
                .map(MetricItemView::from)
                .toList();
    }

    /**
     * CSV 输出（只含聚合数）。
     *
     * @param snapshots 快照
     * @return CSV 文本
     */
    private String toCsv(List<MetricSnapshot> snapshots) {
        StringBuilder builder = new StringBuilder("\uFEFF").append(CSV_HEADER).append('\n');
        for (MetricSnapshot snapshot : snapshots) {
            builder.append(snapshot.getStatDate()).append(',')
                    .append(escape(snapshot.getScopeName())).append(',')
                    .append(snapshot.getMetricType()).append(',')
                    .append(escape(MetricType.labelOf(snapshot.getMetricType()))).append(',')
                    .append(snapshot.getNumerator().stripTrailingZeros().toPlainString()).append(',')
                    .append(snapshot.getDenominator().stripTrailingZeros().toPlainString()).append(',')
                    .append(snapshot.rate().toPlainString()).append('\n');
        }
        return builder.toString();
    }

    /**
     * CSV 字段转义。
     *
     * @param value 原始值
     * @return 转义值
     */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.contains(",") || value.contains("\"")
                ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }

    /**
     * 校验导出区间。
     *
     * @param from 起始日
     * @param to 结束日
     */
    private void assertRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw PartyErrorCode.exception(PartyErrorCode.METRIC_INVALID, "起始日不能晚于结束日");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > properties.getExportMaxDays()) {
            throw PartyErrorCode.exception(PartyErrorCode.METRIC_EXPORT_LIMIT,
                    "导出区间不得超过 " + properties.getExportMaxDays() + " 天");
        }
    }

    /**
     * 解析党务数据范围（支部书记强制本支部）。
     *
     * @param branchId 请求支部 ID
     * @param actor 操作人
     * @return 范围
     */
    private Scope resolvePartyScope(Long branchId, PartyActor actor) {
        if (actor == null) {
            throw PartyErrorCode.exception(PartyErrorCode.ACTOR_REQUIRED);
        }
        if (actor.canManageAll()) {
            if (branchId == null || branchId <= 0) {
                return new Scope(MetricScope.ALL, MetricSnapshot.ALL_SCOPE_ID, ALL_SCOPE_NAME);
            }
            return new Scope(MetricScope.BRANCH, branchId, branchName(branchId));
        }
        Long own = memberAppService.branchIdOf(actor.requireUserId());
        if (own == null || own <= 0) {
            throw PartyErrorCode.exception(PartyErrorCode.BRANCH_NOT_FOUND,
                    "当前账号未关联支部，无法查看本支部看板");
        }
        return new Scope(MetricScope.BRANCH, own, branchName(own));
    }

    /**
     * 支部名称（不存在时抛错）。
     *
     * @param branchId 支部 ID
     * @return 支部名称
     */
    private String branchName(Long branchId) {
        return branchAppService.activeScopes().stream()
                .filter(branch -> branch.getBranchId().equals(branchId))
                .map(BranchScopeView::getBranchName)
                .findFirst()
                .orElseThrow(() -> PartyErrorCode.exception(PartyErrorCode.BRANCH_NOT_FOUND,
                        "支部不存在或已停用"));
    }

    /**
     * 需求门禁。
     */
    private void assertGate() {
        if (!properties.isDemandGatePassed()) {
            throw PartyErrorCode.exception(PartyErrorCode.GATE_CLOSED);
        }
    }

    /**
     * 统计范围。
     */
    private static final class Scope {

        /** 范围类型 */
        private final MetricScope type;

        /** 范围 ID */
        private final Long id;

        /** 范围名称 */
        private final String name;

        /**
         * 构造范围。
         *
         * @param type 范围类型
         * @param id 范围 ID
         * @param name 范围名称
         */
        private Scope(MetricScope type, Long id, String name) {
            this.type = type;
            this.id = id;
            this.name = name;
        }
    }
}
