package com.bluewhale.lms.credit.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.credit.application.command.CreditAccrueCommand;
import com.bluewhale.lms.credit.application.service.CreditAppService;
import com.bluewhale.lms.credit.application.view.CreditLedgerView;
import com.bluewhale.lms.credit.application.view.CreditMetricsView;
import com.bluewhale.lms.credit.application.view.CreditSummaryView;
import com.bluewhale.lms.credit.domain.model.CreditBucket;
import com.bluewhale.lms.credit.domain.model.CreditLedger;
import com.bluewhale.lms.credit.domain.model.CreditLedgerQuery;
import com.bluewhale.lms.credit.domain.model.CreditRule;
import com.bluewhale.lms.credit.domain.model.CreditSource;
import com.bluewhale.lms.credit.domain.repository.CreditLedgerRepository;
import com.bluewhale.lms.credit.domain.repository.CreditRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 学分台账应用服务实现。
 *
 * <p>记账是「把已达成的事实折算成学分」，因此这里守三条：</p>
 * <ol>
 *   <li><b>同一事件只记一次</b>：先查后插，配合唯一键兜底。并发下两个请求都查到「没有」
 *       时，唯一键会让其中一个失败，失败方按「已存在」返回既有记录——重复提交对调用方
 *       表现为成功且不重复记分，这正是幂等该有的样子；</li>
 *   <li><b>没有规则不记分</b>：宁可直接报错让人去配规则，也不要按 0 分静默落一条台账；
 *       0 分记录会污染台账，事后无法区分「确实 0 分」和「规则漏配」；</li>
 *   <li><b>汇总从台账算</b>：不存汇总快照，避免台账与汇总两份数据对不上。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditAppServiceImpl implements CreditAppService {

    /** 学分分布固定档位（与 Mapper 中的 CASE 分支一一对应，缺档补零） */
    private static final List<String> BUCKET_LABELS = List.of("0-5", "5-10", "10-20", "20+");

    private final CreditLedgerRepository creditLedgerRepository;

    private final CreditRuleRepository creditRuleRepository;

    private final LmsProperties properties;

    @Override
    @Transactional
    public Long accrue(CreditAccrueCommand command, LmsActor actor) {
        assertGate();
        Long operator = actor.requireUserId();
        CreditSource source = CreditSource.require(command.getSourceType());
        Long userId = command.getUserId();
        if (userId == null || userId <= 0) {
            throw LmsErrorCode.exception(LmsErrorCode.CREDIT_INVALID, "学员不能为空");
        }
        Optional<CreditLedger> existing = creditLedgerRepository.findBySource(userId, source,
                command.getSourceId());
        if (existing.isPresent()) {
            return existing.get().getId();
        }
        CreditRule rule = creditRuleRepository.findEffective(source, command.getSourceId())
                .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.CREDIT_RULE_MISSING,
                        "该" + sourceLabel(source) + "未配置学分规则，请先维护规则后再记账"));
        rule.requireAccruable();
        CreditLedger ledger = CreditLedger.of(rule, userId, command.getUsername(), command.getSourceId(),
                command.getOccurredAt(), operator, command.getRemark());
        try {
            return creditLedgerRepository.save(ledger);
        } catch (DuplicateKeyException ex) {
            // 并发重复提交：另一请求已写入，按幂等返回既有记录
            return creditLedgerRepository.findBySource(userId, source, command.getSourceId())
                    .map(CreditLedger::getId)
                    .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.CREDIT_INVALID,
                            "学分台账写入冲突，请重试"));
        }
    }

    @Override
    public PageResult<CreditLedgerView> page(CreditLedgerQuery query, LmsActor actor) {
        assertGate();
        return doPage(query);
    }

    @Override
    public CreditSummaryView summary(Long userId, Integer periodYear, LmsActor actor) {
        assertGate();
        Long target = resolveTargetUser(userId, actor);
        List<CreditLedger> ledgers = creditLedgerRepository.listByUser(target, periodYear);
        return summarize(target, periodYear, ledgers);
    }

    @Override
    public PageResult<CreditLedgerView> mine(CreditLedgerQuery query, LmsActor actor) {
        assertGate();
        query.setUserId(actor.requireUserId());
        return doPage(query);
    }

    @Override
    public void accrueQuietly(CreditSource source, Long sourceId, Long userId, String username) {
        if (source == null || sourceId == null || sourceId <= 0 || userId == null || userId <= 0) {
            return;
        }
        if (!properties.isDemandGatePassed()) {
            return;
        }
        try {
            if (creditLedgerRepository.findBySource(userId, source, sourceId).isPresent()) {
                return;
            }
            Optional<CreditRule> rule = creditRuleRepository.findEffective(source, sourceId);
            if (rule.isEmpty() || rule.get().getStatus() == null || !rule.get().getStatus().canAccrue()) {
                // 没配规则或规则已停用：属于管理端待办，不是学员的错误，静默跳过
                return;
            }
            creditLedgerRepository.save(CreditLedger.of(rule.get(), userId, username, sourceId,
                    LocalDateTime.now(), null, "系统自动记账"));
        } catch (DuplicateKeyException ex) {
            // 并发重复触发：另一路已写入，等价于记账成功
        } catch (RuntimeException ex) {
            log.warn("自动记学分失败，已忽略: source={}, sourceId={}, userId={}, error={}",
                    source, sourceId, userId, ex.getMessage());
        }
    }

    @Override
    public CreditMetricsView metrics() {
        assertGate();
        CreditMetricsView view = new CreditMetricsView();
        view.setTotalCredit(creditLedgerRepository.sumTotal());
        view.setEarners(creditLedgerRepository.countEarners());
        view.setDistribution(fillBuckets(creditLedgerRepository.distribution()));
        return view;
    }

    /**
     * 补齐空档：SQL 只返回有人的档位，缺档直接不出现，看板图上会凭空少一根柱子。
     *
     * @param rows 数据库返回的档位
     * @return 固定四档、按档位顺序排列的分布
     */
    private List<CreditBucket> fillBuckets(List<CreditBucket> rows) {
        Map<String, Long> counted = new LinkedHashMap<>();
        if (rows != null) {
            for (CreditBucket row : rows) {
                if (row != null && row.getLabel() != null) {
                    counted.merge(row.getLabel(), row.getEarners(), Long::sum);
                }
            }
        }
        List<CreditBucket> buckets = new ArrayList<>();
        for (String label : BUCKET_LABELS) {
            CreditBucket bucket = new CreditBucket();
            bucket.setLabel(label);
            bucket.setEarners(counted.getOrDefault(label, 0L));
            buckets.add(bucket);
        }
        return buckets;
    }

    /**
     * 分页查询台账。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    private PageResult<CreditLedgerView> doPage(CreditLedgerQuery query) {
        long current = Math.max(query.getCurrent() == null ? 1L : query.getCurrent(), 1L);
        long size = Math.min(Math.max(query.getSize() == null ? 20L : query.getSize(), 1L),
                properties.getCertPageLimit());
        long total = creditLedgerRepository.count(query);
        List<CreditLedgerView> views = creditLedgerRepository.page(query, (current - 1) * size, (int) size)
                .stream().map(CreditLedgerView::from).toList();
        return PageResult.of(views, total, current, size);
    }

    /**
     * 解析汇总对象：未指定时取当前登录人，指定他人时要求管理角色。
     *
     * @param userId 请求的用户 ID
     * @param actor 操作人
     * @return 目标用户 ID
     */
    private Long resolveTargetUser(Long userId, LmsActor actor) {
        if (userId == null) {
            return actor.requireUserId();
        }
        Long self = actor.getUserId();
        if (!userId.equals(self) && !actor.canManageAll()) {
            throw LmsErrorCode.exception(LmsErrorCode.CREDIT_INVALID, "无权查看他人学分");
        }
        return userId;
    }

    /**
     * 聚合台账为年度汇总。
     *
     * @param userId 学员用户 ID
     * @param periodYear 归属年度
     * @param ledgers 台账列表
     * @return 汇总
     */
    private CreditSummaryView summarize(Long userId, Integer periodYear, List<CreditLedger> ledgers) {
        CreditSummaryView view = new CreditSummaryView();
        view.setUserId(userId);
        view.setPeriodYear(periodYear);
        Map<String, CreditSummaryView.SourceCredit> grouped = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CreditLedger ledger : ledgers) {
            String key = ledger.getSourceType() == null ? "UNKNOWN" : ledger.getSourceType().name();
            BigDecimal credit = ledger.getCredit() == null ? BigDecimal.ZERO : ledger.getCredit();
            CreditSummaryView.SourceCredit item = grouped.computeIfAbsent(key, name -> {
                CreditSummaryView.SourceCredit created = new CreditSummaryView.SourceCredit();
                created.setSourceType(name);
                created.setCredit(BigDecimal.ZERO);
                return created;
            });
            item.setCredit(item.getCredit().add(credit));
            item.setCount(item.getCount() + 1);
            total = total.add(credit);
        }
        view.setItems(List.copyOf(grouped.values()));
        view.setTotalCredit(total);
        return view;
    }

    /**
     * 来源类型中文标签（用于未配置规则时的提示）。
     *
     * @param source 来源类型
     * @return 标签
     */
    private String sourceLabel(CreditSource source) {
        return switch (source) {
            case COURSE -> "课程";
            case EXAM -> "考试";
            case TRAINING -> "培训班";
        };
    }

    /**
     * 断言需求门禁通过。
     */
    private void assertGate() {
        if (!properties.isDemandGatePassed()) {
            throw LmsErrorCode.exception(LmsErrorCode.GATE_CLOSED);
        }
    }
}
