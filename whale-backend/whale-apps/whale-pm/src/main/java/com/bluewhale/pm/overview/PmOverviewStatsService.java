package com.bluewhale.pm.overview;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.pm.domain.model.WorkItemType;
import com.bluewhale.pm.infrastructure.entity.PmProject;
import com.bluewhale.pm.infrastructure.entity.PmSprint;
import com.bluewhale.pm.infrastructure.entity.PmStatus;
import com.bluewhale.pm.infrastructure.entity.PmWorkItem;
import com.bluewhale.pm.infrastructure.mapper.PmProjectMapper;
import com.bluewhale.pm.infrastructure.mapper.PmSprintMapper;
import com.bluewhale.pm.infrastructure.mapper.PmStatusMapper;
import com.bluewhale.pm.infrastructure.mapper.PmWorkItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 营造坊（PM）统计口径：项目、迭代、工作项三本账。
 *
 * <p><b>口径只在这里定义一次。</b>"未完成工作项"的判定是
 * {@code 状态分类 != DONE}——PM 的状态是每个项目自定义的（pm_status 按
 * projectId 各配一套），唯一跨项目稳定的只有 category 三分类
 * TODO / IN_PROGRESS / DONE；直接拿状态码或状态名判断，
 * 换个项目的自定义状态就全错了。2D 看板按分类分列，这里聚合成一个数，
 * 两处必须同一个判定。</p>
 *
 * <p><b>不写 JOIN，先取 DONE 分类再 IN。</b>工作项表只有 statusId，
 * 分类在状态表上。用 notInSql 子查询会绕开租户隔离插件（插件不改写
 * 字符串子查询），跨租户串数且不报错；先 selectList 取本租户的
 * DONE 状态 id（插件自动加 tenant_id），再对工作项做 IN / NOT IN。</p>
 *
 * <p><b>不写 GROUP BY，按类型各 count 一次。</b>工作项类型只有五种且码值固定，
 * 五次索引 count 比 CSV 聚合直观；更重要的是不走自定义 SQL 就不用自己拼
 * {@code tenant_id} 条件。</p>
 *
 * <p>统计只读，不写库：概览被前端按 30s 轮询，任何写操作都会变成"每 30 秒写一次"。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class PmOverviewStatsService {

    /** 项目状态：在建 */
    private static final String PROJECT_ACTIVE = "ACTIVE";

    /** 项目状态：已归档 */
    private static final String PROJECT_ARCHIVED = "ARCHIVED";

    /** 迭代状态：进行中 */
    private static final String SPRINT_ACTIVE = "ACTIVE";

    /** 迭代状态：未启动 */
    private static final String SPRINT_FUTURE = "FUTURE";

    /** 迭代状态：已关闭 */
    private static final String SPRINT_CLOSED = "CLOSED";

    /** 状态分类：未完成（TODO + IN_PROGRESS） */
    private static final String CATEGORY_DONE = "DONE";

    /** 状态分类：未开始 */
    private static final String CATEGORY_TODO = "TODO";

    /** 状态分类：进行中 */
    private static final String CATEGORY_IN_PROGRESS = "IN_PROGRESS";

    /** 高优先级前两级 */
    private static final List<String> HIGH_PRIORITY = List.of("HIGHEST", "HIGH");

    private final PmProjectMapper projectMapper;

    private final PmSprintMapper sprintMapper;

    private final PmStatusMapper statusMapper;

    private final PmWorkItemMapper workItemMapper;

    /**
     * 取一次统计快照。
     *
     * @return 统计快照
     */
    public PmOverviewStats snapshot() {
        long projectActive = count(projectMapper.selectCount(new LambdaQueryWrapper<PmProject>()
                .eq(PmProject::getStatus, PROJECT_ACTIVE)));
        long projectArchived = count(projectMapper.selectCount(new LambdaQueryWrapper<PmProject>()
                .eq(PmProject::getStatus, PROJECT_ARCHIVED)));
        long sprintActive = count(sprintMapper.selectCount(new LambdaQueryWrapper<PmSprint>()
                .eq(PmSprint::getStatus, SPRINT_ACTIVE)));
        long sprintFuture = count(sprintMapper.selectCount(new LambdaQueryWrapper<PmSprint>()
                .eq(PmSprint::getStatus, SPRINT_FUTURE)));
        long sprintClosed = count(sprintMapper.selectCount(new LambdaQueryWrapper<PmSprint>()
                .eq(PmSprint::getStatus, SPRINT_CLOSED)));

        List<Long> doneIds = doneStatusIds();
        long itemTotal = count(workItemMapper.selectCount(null));
        long itemDone = doneIds.isEmpty() ? 0L
                : count(workItemMapper.selectCount(new LambdaQueryWrapper<PmWorkItem>()
                        .in(PmWorkItem::getStatusId, doneIds)));
        long itemTodo = countByCategory(CATEGORY_TODO, doneIds);
        long itemInProgress = countByCategory(CATEGORY_IN_PROGRESS, doneIds);
        long itemOverdue = countOpen(w -> w.lt(PmWorkItem::getDueDate, LocalDate.now()), doneIds);
        long itemOverdueHigh = countOpen(w -> w.lt(PmWorkItem::getDueDate, LocalDate.now())
                .in(PmWorkItem::getPriority, HIGH_PRIORITY), doneIds);
        long itemUnassigned = countOpen(w -> w.isNull(PmWorkItem::getAssigneeId), doneIds);
        return new PmOverviewStats(projectActive, projectArchived,
                sprintActive, sprintFuture, sprintClosed,
                itemTotal, itemDone, itemTodo, itemInProgress,
                itemOverdue, itemOverdueHigh, itemUnassigned, itemByType(doneIds));
    }

    /**
     * 本租户所有"已完成"分类的状态 id。
     *
     * <p>租户隔离插件自动加 {@code tenant_id}；没有任何 DONE 分类时返回空表——
     * 此时"已完成"就是 0，而不是把全部工作项当未完成处理之外还报错。</p>
     *
     * @return DONE 分类的状态 id 列表（可能为空）
     */
    private List<Long> doneStatusIds() {
        return statusMapper.selectList(new LambdaQueryWrapper<PmStatus>()
                .eq(PmStatus::getCategory, CATEGORY_DONE))
                .stream().map(PmStatus::getId).toList();
    }

    /**
     * 按状态分类计未完成工作项。
     *
     * @param category 状态分类（TODO / IN_PROGRESS）
     * @param doneIds  DONE 分类的状态 id
     * @return 计数
     */
    private long countByCategory(String category, List<Long> doneIds) {
        List<Long> ids = statusMapper.selectList(new LambdaQueryWrapper<PmStatus>()
                .eq(PmStatus::getCategory, category))
                .stream().map(PmStatus::getId).toList();
        if (ids.isEmpty()) {
            return 0L;
        }
        return count(workItemMapper.selectCount(new LambdaQueryWrapper<PmWorkItem>()
                .in(PmWorkItem::getStatusId, ids)));
    }

    /**
     * 带"未完成"限定的计数：先套调用方给的条件，再排除已完成状态。
     *
     * @param extra   调用方条件（逾期 / 高优 / 无主）
     * @param doneIds DONE 分类的状态 id
     * @return 计数
     */
    private long countOpen(Consumer<LambdaQueryWrapper<PmWorkItem>> extra,
            List<Long> doneIds) {
        LambdaQueryWrapper<PmWorkItem> wrapper = new LambdaQueryWrapper<>();
        extra.accept(wrapper);
        if (!doneIds.isEmpty()) {
            wrapper.notIn(PmWorkItem::getStatusId, doneIds);
        }
        return count(workItemMapper.selectCount(wrapper));
    }

    /**
     * 未完成工作项按类型计数：史诗 / 需求 / 任务 / 缺陷 / 子任务。
     *
     * <p>只数未完成：完成项的类型分布是历史账，混进来会让
     * "缺陷 8 个"里有一半其实早就修完了。</p>
     *
     * @param doneIds DONE 分类的状态 id
     * @return 五个类型计数，顺序固定（与屏风图例一致）
     */
    private List<PmOverviewStats.TypeCount> itemByType(List<Long> doneIds) {
        List<PmOverviewStats.TypeCount> counts = new ArrayList<>(5);
        appendType(counts, "史诗", WorkItemType.EPIC.name(), doneIds);
        appendType(counts, "需求", WorkItemType.STORY.name(), doneIds);
        appendType(counts, "任务", WorkItemType.TASK.name(), doneIds);
        appendType(counts, "缺陷", WorkItemType.BUG.name(), doneIds);
        appendType(counts, "子任务", WorkItemType.SUBTASK.name(), doneIds);
        return counts;
    }

    /**
     * 数一个类型并追加。
     *
     * @param counts   目标列表
     * @param label    中文标签
     * @param itemType 类型码值
     * @param doneIds  DONE 分类的状态 id
     */
    private void appendType(List<PmOverviewStats.TypeCount> counts, String label,
            String itemType, List<Long> doneIds) {
        LambdaQueryWrapper<PmWorkItem> wrapper = new LambdaQueryWrapper<PmWorkItem>()
                .eq(PmWorkItem::getType, itemType);
        if (!doneIds.isEmpty()) {
            wrapper.notIn(PmWorkItem::getStatusId, doneIds);
        }
        counts.add(new PmOverviewStats.TypeCount(label, count(workItemMapper.selectCount(wrapper))));
    }

    /**
     * 计数兜底：{@code selectCount} 在极端情况下返回 {@code null}，直接拆箱就是 NPE。
     *
     * @param value 计数值，可空
     * @return 非空计数
     */
    private static long count(Long value) {
        return value == null ? 0L : value;
    }
}
