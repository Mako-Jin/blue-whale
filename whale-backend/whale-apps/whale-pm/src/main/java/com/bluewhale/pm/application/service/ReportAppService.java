package com.bluewhale.pm.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.cache.CacheConsistencyTemplate;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pm.domain.model.SprintStatus;
import com.bluewhale.pm.domain.model.StatusCategory;
import com.bluewhale.pm.infrastructure.entity.PmActivity;
import com.bluewhale.pm.infrastructure.entity.PmSprint;
import com.bluewhale.pm.infrastructure.entity.PmStatus;
import com.bluewhale.pm.infrastructure.entity.PmWorkItem;
import com.bluewhale.pm.infrastructure.mapper.PmActivityMapper;
import com.bluewhale.pm.infrastructure.mapper.PmSprintMapper;
import com.bluewhale.pm.infrastructure.mapper.PmStatusMapper;
import com.bluewhale.pm.infrastructure.mapper.PmWorkItemMapper;
import com.bluewhale.pm.interfaces.vo.BurndownPointVO;
import com.bluewhale.pm.interfaces.vo.BurndownVO;
import com.bluewhale.pm.interfaces.vo.CumulativeFlowSeriesVO;
import com.bluewhale.pm.interfaces.vo.CumulativeFlowVO;
import com.bluewhale.pm.interfaces.vo.VelocitySprintVO;
import com.bluewhale.pm.interfaces.vo.VelocityVO;
import com.bluewhale.pm.interfaces.vo.WorkloadMemberVO;
import com.bluewhale.pm.interfaces.vo.WorkloadVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 报表应用服务：燃尽、速率、累积流与成员负载（MySQL 聚合 + Redis 5 分钟缓存）。
 *
 * <p>报表只读，不做审计；历史状态由 {@code pm_activity} 的流转记录反推，
 * 空数据统一返回空序列，查询区间上限 1 年。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportAppService {

    /** 报表缓存存活时长 */
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    /** 查询区间上限（天） */
    private static final int MAX_RANGE_DAYS = 366;

    /** 累积流图默认回看天数 */
    private static final int DEFAULT_FLOW_DAYS = 30;

    /** 速率图默认迭代数量 */
    private static final int DEFAULT_VELOCITY_LIMIT = 5;

    /** 速率图迭代数量上限 */
    private static final int MAX_VELOCITY_LIMIT = 12;

    /** 动态动作：状态流转 */
    private static final String ACTION_TRANSITION = "TRANSITION";

    /** 状态分类：待处理 */
    private static final String CATEGORY_TODO = StatusCategory.TODO.name();

    /** 状态分类：处理中 */
    private static final String CATEGORY_IN_PROGRESS = StatusCategory.IN_PROGRESS.name();

    /** 状态分类：已完成 */
    private static final String CATEGORY_DONE = StatusCategory.DONE.name();

    /** 故事点保留小数位 */
    private static final int POINT_SCALE = 2;

    private final PmWorkItemMapper workItemMapper;

    private final PmStatusMapper statusMapper;

    private final PmSprintMapper sprintMapper;

    private final PmActivityMapper activityMapper;

    private final ProjectAccessGuard projectAccessGuard;

    private final CacheConsistencyTemplate cacheTemplate;

    private final ObjectMapper objectMapper;

    /**
     * 迭代燃尽图：按天统计剩余故事点与理想线。
     *
     * @param projectId  项目ID
     * @param sprintId   迭代ID，为空时取进行中迭代
     * @param operatorId 当前用户ID
     * @return 燃尽图数据
     */
    public BurndownVO burndown(Long projectId, Long sprintId, Long operatorId) {
        projectAccessGuard.requireReadable(projectId, operatorId);
        PmSprint sprint = resolveSprint(projectId, sprintId);
        if (sprint == null) {
            return emptyBurndown();
        }
        String key = projectId + ":" + sprint.getId();
        return cached("pm:report:burndown", key, () -> buildBurndown(sprint), BurndownVO.class);
    }

    /**
     * 迭代速率图：最近若干已关闭迭代的承诺与完成故事点。
     *
     * @param projectId  项目ID
     * @param limit      迭代数量，为空取默认 5，上限 12
     * @param operatorId 当前用户ID
     * @return 速率图数据
     */
    public VelocityVO velocity(Long projectId, Integer limit, Long operatorId) {
        projectAccessGuard.requireReadable(projectId, operatorId);
        int size = limit == null ? DEFAULT_VELOCITY_LIMIT : Math.min(Math.max(limit, 1), MAX_VELOCITY_LIMIT);
        String key = projectId + ":" + size;
        return cached("pm:report:velocity", key, () -> buildVelocity(projectId, size), VelocityVO.class);
    }

    /**
     * 累积流图：按状态分类统计每日工作项数量。
     *
     * @param projectId  项目ID
     * @param sprintId   迭代ID，为空时统计整个项目
     * @param startDate  统计开始日期，为空时取迭代区间或最近 30 天
     * @param endDate    统计结束日期
     * @param operatorId 当前用户ID
     * @return 累积流图数据
     */
    public CumulativeFlowVO cumulativeFlow(Long projectId, Long sprintId, LocalDate startDate, LocalDate endDate,
            Long operatorId) {
        projectAccessGuard.requireReadable(projectId, operatorId);
        PmSprint sprint = resolveSprint(projectId, sprintId);
        LocalDate[] range = resolveFlowRange(sprint, startDate, endDate);
        String key = projectId + ":" + (sprint == null ? "-" : sprint.getId()) + ":" + range[0] + ":" + range[1];
        return cached("pm:report:cumulative-flow", key,
                () -> buildCumulativeFlow(projectId, sprint, range[0], range[1]), CumulativeFlowVO.class);
    }

    /**
     * 成员负载图：按负责人聚合工作项数量与故事点。
     *
     * @param projectId  项目ID
     * @param operatorId 当前用户ID
     * @return 成员负载数据
     */
    public WorkloadVO workload(Long projectId, Long operatorId) {
        projectAccessGuard.requireReadable(projectId, operatorId);
        return cached("pm:report:workload", String.valueOf(projectId),
                () -> buildWorkload(projectId), WorkloadVO.class);
    }

    /**
     * 构建燃尽图数据。
     *
     * @param sprint 迭代实体
     * @return 燃尽图数据
     */
    private BurndownVO buildBurndown(PmSprint sprint) {
        BurndownVO vo = emptyBurndown();
        vo.setSprintId(String.valueOf(sprint.getId()));
        vo.setSprintName(sprint.getName());
        vo.setStartDate(sprint.getStartDate());
        vo.setEndDate(sprint.getEndDate());
        if (sprint.getStartDate() == null || sprint.getEndDate() == null) {
            return vo;
        }
        List<PmWorkItem> items = listItems(sprint.getProjectId(), sprint.getId());
        BigDecimal total = sumPoints(items);
        vo.setTotalPoints(total);
        if (items.isEmpty()) {
            return vo;
        }
        Map<Long, PmStatus> statuses = statusMap(sprint.getProjectId());
        Map<String, String> categories = categoryMap(statuses);
        Map<Long, List<PmActivity>> transitions = transitionsByItem(items);
        LocalDate start = sprint.getStartDate();
        LocalDate last = sprint.getEndDate().isBefore(LocalDate.now()) ? sprint.getEndDate() : LocalDate.now();
        if (last.isBefore(start)) {
            return vo;
        }
        long totalDays = Math.max(ChronoUnit.DAYS.between(start, sprint.getEndDate()), 1L);
        List<BurndownPointVO> points = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(last); day = day.plusDays(1)) {
            BigDecimal remaining = BigDecimal.ZERO;
            for (PmWorkItem item : items) {
                String code = statusCodeAt(item, day, statuses, transitions.get(item.getId()));
                if (code != null && !CATEGORY_DONE.equals(categoryOf(code, categories))) {
                    remaining = remaining.add(pointsOf(item));
                }
            }
            long elapsed = ChronoUnit.DAYS.between(start, day);
            long left = Math.max(totalDays - elapsed, 0L);
            BigDecimal ideal = total.multiply(BigDecimal.valueOf(left))
                    .divide(BigDecimal.valueOf(totalDays), POINT_SCALE, RoundingMode.HALF_UP);
            BurndownPointVO point = new BurndownPointVO();
            point.setDate(day);
            point.setRemainingPoints(remaining.setScale(POINT_SCALE, RoundingMode.HALF_UP));
            point.setIdealPoints(ideal);
            points.add(point);
        }
        vo.setPoints(points);
        return vo;
    }

    /**
     * 构建速率图数据。
     *
     * @param projectId 项目ID
     * @param limit     迭代数量
     * @return 速率图数据
     */
    private VelocityVO buildVelocity(Long projectId, int limit) {
        List<PmSprint> sprints = sprintMapper.selectList(new LambdaQueryWrapper<PmSprint>()
                .eq(PmSprint::getProjectId, projectId)
                .eq(PmSprint::getStatus, SprintStatus.CLOSED.name())
                .orderByDesc(PmSprint::getEndDate)
                .orderByDesc(PmSprint::getId)
                .last("LIMIT " + limit));
        List<PmSprint> ordered = new ArrayList<>(sprints);
        ordered.sort(Comparator.comparing(PmSprint::getEndDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PmSprint::getId));
        VelocityVO vo = new VelocityVO();
        List<VelocitySprintVO> records = new ArrayList<>();
        Map<Long, PmStatus> statuses = statusMap(projectId);
        Map<String, String> categories = categoryMap(statuses);
        for (PmSprint sprint : ordered) {
            List<PmWorkItem> items = listItems(projectId, sprint.getId());
            BigDecimal committed = sumPoints(items);
            BigDecimal completed = BigDecimal.ZERO;
            for (PmWorkItem item : items) {
                PmStatus status = statuses.get(item.getStatusId());
                if (status != null && CATEGORY_DONE.equals(categoryOf(status.getCode(), categories))) {
                    completed = completed.add(pointsOf(item));
                }
            }
            VelocitySprintVO record = new VelocitySprintVO();
            record.setSprintId(String.valueOf(sprint.getId()));
            record.setSprintName(sprint.getName());
            record.setStartDate(sprint.getStartDate());
            record.setEndDate(sprint.getEndDate());
            record.setCommittedPoints(committed);
            record.setCompletedPoints(completed);
            records.add(record);
        }
        vo.setSprints(records);
        return vo;
    }

    /**
     * 构建累积流图数据。
     *
     * @param projectId 项目ID
     * @param sprint    迭代实体，可为 {@code null}
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 累积流图数据
     */
    private CumulativeFlowVO buildCumulativeFlow(Long projectId, PmSprint sprint, LocalDate startDate,
            LocalDate endDate) {
        CumulativeFlowVO vo = new CumulativeFlowVO();
        vo.setStartDate(startDate);
        vo.setEndDate(endDate);
        List<PmWorkItem> items = listItems(projectId, sprint == null ? null : sprint.getId());
        Map<Long, PmStatus> statuses = statusMap(projectId);
        Map<String, String> categories = categoryMap(statuses);
        Map<Long, List<PmActivity>> transitions = transitionsByItem(items);
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            dates.add(day);
        }
        Map<String, List<Integer>> series = new LinkedHashMap<>();
        series.put(CATEGORY_TODO, new ArrayList<>());
        series.put(CATEGORY_IN_PROGRESS, new ArrayList<>());
        series.put(CATEGORY_DONE, new ArrayList<>());
        for (LocalDate day : dates) {
            Map<String, Integer> counts = new HashMap<>();
            counts.put(CATEGORY_TODO, 0);
            counts.put(CATEGORY_IN_PROGRESS, 0);
            counts.put(CATEGORY_DONE, 0);
            for (PmWorkItem item : items) {
                String code = statusCodeAt(item, day, statuses, transitions.get(item.getId()));
                if (code == null) {
                    continue;
                }
                String category = categoryOf(code, categories);
                counts.merge(category, 1, Integer::sum);
            }
            series.get(CATEGORY_TODO).add(counts.get(CATEGORY_TODO));
            series.get(CATEGORY_IN_PROGRESS).add(counts.get(CATEGORY_IN_PROGRESS));
            series.get(CATEGORY_DONE).add(counts.get(CATEGORY_DONE));
        }
        vo.setDates(dates);
        List<CumulativeFlowSeriesVO> result = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> entry : series.entrySet()) {
            CumulativeFlowSeriesVO item = new CumulativeFlowSeriesVO();
            item.setCategory(entry.getKey());
            item.setValues(entry.getValue());
            result.add(item);
        }
        vo.setSeries(result);
        return vo;
    }

    /**
     * 构建成员负载数据。
     *
     * @param projectId 项目ID
     * @return 成员负载数据
     */
    private WorkloadVO buildWorkload(Long projectId) {
        List<PmWorkItem> items = listItems(projectId, null);
        Map<Long, PmStatus> statuses = statusMap(projectId);
        Map<String, String> categories = categoryMap(statuses);
        Map<Long, WorkloadMemberVO> members = new HashMap<>();
        for (PmWorkItem item : items) {
            WorkloadMemberVO member = members.computeIfAbsent(item.getAssigneeId(), key -> newMember(item));
            PmStatus status = statuses.get(item.getStatusId());
            String category = status == null ? CATEGORY_TODO : categoryOf(status.getCode(), categories);
            member.setTotalCount(member.getTotalCount() + 1);
            if (CATEGORY_DONE.equals(category)) {
                member.setDoneCount(member.getDoneCount() + 1);
                member.setDonePoints(member.getDonePoints().add(pointsOf(item)));
            } else {
                member.setOpenCount(member.getOpenCount() + 1);
                member.setOpenPoints(member.getOpenPoints().add(pointsOf(item)));
            }
        }
        List<WorkloadMemberVO> result = new ArrayList<>(members.values());
        result.sort(Comparator.comparing(WorkloadMemberVO::getOpenCount, Comparator.reverseOrder())
                .thenComparing(WorkloadMemberVO::getTotalCount, Comparator.reverseOrder()));
        WorkloadVO vo = new WorkloadVO();
        vo.setMembers(result);
        return vo;
    }

    /**
     * 解析迭代：指定时必须属于项目，未指定时取进行中迭代。
     *
     * @param projectId 项目ID
     * @param sprintId  迭代ID，可为 {@code null}
     * @return 迭代实体，无进行中迭代时返回 {@code null}
     */
    private PmSprint resolveSprint(Long projectId, Long sprintId) {
        if (sprintId != null) {
            PmSprint sprint = sprintMapper.selectById(sprintId);
            if (sprint == null || !projectId.equals(sprint.getProjectId())) {
                throw new BizException(ErrorCode.NOT_FOUND, "biz.pm.application.iteration-found", "迭代不存在");
            }
            return sprint;
        }
        return sprintMapper.selectOne(new LambdaQueryWrapper<PmSprint>()
                .eq(PmSprint::getProjectId, projectId)
                .eq(PmSprint::getStatus, SprintStatus.ACTIVE.name())
                .last("LIMIT 1"));
    }

    /**
     * 解析累积流图统计区间：优先入参，其次迭代区间，最后最近 30 天。
     *
     * @param sprint    迭代实体，可为 {@code null}
     * @param startDate 入参开始日期，可为 {@code null}
     * @param endDate   入参结束日期，可为 {@code null}
     * @return 长度为 2 的日期数组（开始、结束）
     */
    private LocalDate[] resolveFlowRange(PmSprint sprint, LocalDate startDate, LocalDate endDate) {
        boolean hasStart = startDate != null;
        boolean hasEnd = endDate != null;
        if (hasStart != hasEnd) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pm.application.start-end-dates-need-at-same", "开始与结束日期需同时提供");
        }
        LocalDate start;
        LocalDate end;
        if (hasStart) {
            start = startDate;
            end = endDate;
        } else if (sprint != null && sprint.getStartDate() != null && sprint.getEndDate() != null) {
            start = sprint.getStartDate();
            end = sprint.getEndDate().isBefore(LocalDate.now()) ? sprint.getEndDate() : LocalDate.now();
        } else {
            end = LocalDate.now();
            start = end.minusDays(DEFAULT_FLOW_DAYS - 1L);
        }
        if (end.isBefore(start)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pm.application.end-date-earlier-than-start-date", "结束日期不能早于开始日期");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pm.application.statistics-period-exceed-1-year", "统计区间不能超过 1 年");
        }
        return new LocalDate[] {start, end};
    }

    /**
     * 查询工作项：指定迭代时按迭代过滤，否则取项目全量。
     *
     * @param projectId 项目ID
     * @param sprintId  迭代ID，可为 {@code null}
     * @return 工作项列表
     */
    private List<PmWorkItem> listItems(Long projectId, Long sprintId) {
        return workItemMapper.selectList(new LambdaQueryWrapper<PmWorkItem>()
                .eq(PmWorkItem::getProjectId, projectId)
                .eq(sprintId != null, PmWorkItem::getSprintId, sprintId)
                .orderByAsc(PmWorkItem::getRankValue)
                .orderByAsc(PmWorkItem::getId));
    }

    /**
     * 查询项目状态并转为 ID 映射。
     *
     * @param projectId 项目ID
     * @return 状态映射
     */
    private Map<Long, PmStatus> statusMap(Long projectId) {
        return statusMapper.selectList(new LambdaQueryWrapper<PmStatus>()
                .eq(PmStatus::getProjectId, projectId)).stream()
                .collect(Collectors.toMap(PmStatus::getId, status -> status));
    }

    /**
     * 构建「状态编码 → 状态分类」映射。
     *
     * @param statuses 状态映射
     * @return 分类映射
     */
    private Map<String, String> categoryMap(Map<Long, PmStatus> statuses) {
        Map<String, String> categories = new HashMap<>();
        for (PmStatus status : statuses.values()) {
            categories.put(status.getCode(), status.getCategory());
        }
        return categories;
    }

    /**
     * 查询工作项的状态流转记录并按工作项分组（按时间升序）。
     *
     * @param items 工作项列表
     * @return 工作项ID → 流转记录
     */
    private Map<Long, List<PmActivity>> transitionsByItem(List<PmWorkItem> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        List<Long> itemIds = items.stream().map(PmWorkItem::getId).toList();
        List<PmActivity> activities = activityMapper.selectList(new LambdaQueryWrapper<PmActivity>()
                .in(PmActivity::getWorkItemId, itemIds)
                .eq(PmActivity::getAction, ACTION_TRANSITION)
                .orderByAsc(PmActivity::getCreateTime)
                .orderByAsc(PmActivity::getId));
        return activities.stream().collect(Collectors.groupingBy(PmActivity::getWorkItemId));
    }

    /**
     * 反推工作项在指定日期结束时的状态编码。
     *
     * <p>取该日之后的首条流转记录的旧值；无后续流转时取当前状态；
     * 工作项在该日尚未创建时返回 {@code null}。</p>
     *
     * @param item        工作项
     * @param day         统计日期
     * @param statuses    状态映射
     * @param transitions 该工作项的流转记录（升序），可为 {@code null}
     * @return 状态编码，未创建时返回 {@code null}
     */
    private String statusCodeAt(PmWorkItem item, LocalDate day, Map<Long, PmStatus> statuses,
            List<PmActivity> transitions) {
        LocalDateTime endOfDay = day.atTime(LocalTime.MAX);
        if (item.getCreateTime() != null && item.getCreateTime().isAfter(endOfDay)) {
            return null;
        }
        if (transitions != null) {
            for (PmActivity activity : transitions) {
                if (activity.getCreateTime() != null && activity.getCreateTime().isAfter(endOfDay)) {
                    return activity.getOldValue();
                }
            }
        }
        PmStatus current = statuses.get(item.getStatusId());
        return current == null ? null : current.getCode();
    }

    /**
     * 状态分类归一：未知分类按待处理处理。
     *
     * @param code       状态编码
     * @param categories 分类映射
     * @return 状态分类
     */
    private String categoryOf(String code, Map<String, String> categories) {
        String category = categories.get(code);
        if (CATEGORY_DONE.equals(category) || CATEGORY_IN_PROGRESS.equals(category)) {
            return category;
        }
        return CATEGORY_TODO;
    }

    /**
     * 故事点求和（空值按 0 计）。
     *
     * @param items 工作项列表
     * @return 故事点合计
     */
    private BigDecimal sumPoints(List<PmWorkItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (PmWorkItem item : items) {
            total = total.add(pointsOf(item));
        }
        return total.setScale(POINT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 读取工作项故事点（空值按 0 计）。
     *
     * @param item 工作项
     * @return 故事点
     */
    private BigDecimal pointsOf(PmWorkItem item) {
        return item.getStoryPoints() == null ? BigDecimal.ZERO : item.getStoryPoints();
    }

    /**
     * 新建成员负载行。
     *
     * @param item 工作项（取其负责人）
     * @return 成员负载行
     */
    private WorkloadMemberVO newMember(PmWorkItem item) {
        WorkloadMemberVO member = new WorkloadMemberVO();
        member.setAssigneeId(item.getAssigneeId() == null ? null : String.valueOf(item.getAssigneeId()));
        member.setTotalCount(0);
        member.setOpenCount(0);
        member.setDoneCount(0);
        member.setOpenPoints(BigDecimal.ZERO);
        member.setDonePoints(BigDecimal.ZERO);
        return member;
    }

    /**
     * 构造空燃尽图。
     *
     * @return 燃尽图数据
     */
    private BurndownVO emptyBurndown() {
        BurndownVO vo = new BurndownVO();
        vo.setTotalPoints(BigDecimal.ZERO);
        vo.setPoints(List.of());
        return vo;
    }

    /**
     * 读取缓存，异常或反序列化失败时降级直算。
     *
     * @param name   缓存名称
     * @param key    缓存标识
     * @param loader 回源计算器
     * @param type   结果类型
     * @param <T>    结果类型
     * @return 报表数据
     */
    private <T> T cached(String name, String key, Supplier<T> loader, Class<T> type) {
        try {
            String json = cacheTemplate.getOrLoad(name, key, CACHE_TTL,
                    () -> objectMapper.writeValueAsString(loader.get()));
            if (json == null) {
                return loader.get();
            }
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            log.warn("报表缓存读写失败，降级直算: name={}, key={}, error={}", name, key, ex.getMessage());
            return loader.get();
        }
    }
}
