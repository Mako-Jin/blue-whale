package com.bluewhale.workflow.service.impl;

import com.bluewhale.workflow.mapper.ProcessInstanceMapper;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.po.stat.ProcessDayCountPo;
import com.bluewhale.workflow.po.stat.ProcessInstanceStatPo;
import com.bluewhale.workflow.po.stat.ProcessNodeBacklogPo;
import com.bluewhale.workflow.po.stat.ProcessOverdueStatPo;
import com.bluewhale.workflow.service.ProcessOverviewStatQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@link ProcessOverviewStatQuery} 的 MyBatis 实现。
 *
 * <p>所有查询显式带 {@code tenant_id}，时间一律写成 {@code column >= ? AND column < ?}，
 * 函数不套在条件列上——{@code DATE(create_time) = ?} 会让 {@code idx_tenant_create_time}
 * 直接失效，数据一多就是租户内全表扫。</p>
 *
 * <p>空结果折算成 0 而不是 null：概览是给人看的，"今日办结 null 次"比 0 更像故障。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ProcessOverviewStatQueryImpl implements ProcessOverviewStatQuery {

    /** 节点名缺失时的兜底展示名（历史数据允许为空） */
    private static final String UNNAMED_NODE = "未命名节点";

    private final ProcessInstanceMapper instanceMapper;

    private final ProcessTaskMapper taskMapper;

    @Override
    public InstanceStat instanceStat(Long tenantId, LocalDateTime from, LocalDateTime to) {
        if (invalidWindow(tenantId, from, to)) {
            return new InstanceStat(0L, 0L, 0L);
        }
        ProcessInstanceStatPo row = instanceMapper.statOverview(tenantId, from, to);
        if (row == null) {
            return new InstanceStat(0L, 0L, 0L);
        }
        return new InstanceStat(valueOf(row.getRunning()), valueOf(row.getStartedToday()),
                valueOf(row.getFinishedToday()));
    }

    @Override
    public long pendingCount(Long tenantId) {
        if (tenantId == null) {
            return 0L;
        }
        return taskMapper.countPending(tenantId);
    }

    @Override
    public OverdueStat overdue(Long tenantId, LocalDateTime now) {
        if (tenantId == null || now == null) {
            return new OverdueStat(0L, 0L);
        }
        ProcessOverdueStatPo row = taskMapper.statOverdue(tenantId, now);
        if (row == null) {
            return new OverdueStat(0L, 0L);
        }
        return new OverdueStat(valueOf(row.getOverdue()), valueOf(row.getWorstMinutes()));
    }

    @Override
    public long stuckInstances(Long tenantId) {
        if (tenantId == null) {
            return 0L;
        }
        return instanceMapper.countStuck(tenantId);
    }

    @Override
    public List<DayFlow> dailyFlow(Long tenantId, LocalDateTime from, LocalDateTime to) {
        if (invalidWindow(tenantId, from, to)) {
            return List.of();
        }
        /* 发起与办结分两条 SQL：两列的时间语义不同（create_time / finished_at），
           合成一条要在同一行里同时命中两个索引，代价远大于多跑一条 count。
           这里按日期合并成一张表，省得 provider 各写一遍"两条列表对齐到 7 天"。 */
        Map<LocalDate, long[]> merged = new TreeMap<>();
        for (ProcessDayCountPo row : safe(instanceMapper.countStartedByDay(tenantId, from, to))) {
            if (row.getDay() != null) {
                merged.computeIfAbsent(row.getDay(), day -> new long[2])[0] += valueOf(row.getTotal());
            }
        }
        for (ProcessDayCountPo row : safe(instanceMapper.countFinishedByDay(tenantId, from, to))) {
            if (row.getDay() != null) {
                merged.computeIfAbsent(row.getDay(), day -> new long[2])[1] += valueOf(row.getTotal());
            }
        }
        List<DayFlow> result = new ArrayList<>(merged.size());
        merged.forEach((day, counts) -> result.add(new DayFlow(day, counts[0], counts[1])));
        return List.copyOf(result);
    }

    @Override
    public List<Backlog> pendingBacklog(Long tenantId, int limit) {
        if (tenantId == null || limit <= 0) {
            return List.of();
        }
        List<Backlog> result = new ArrayList<>(limit);
        for (ProcessNodeBacklogPo row : safe(taskMapper.topPendingNodes(tenantId, limit))) {
            String name = row.getNodeName();
            result.add(new Backlog(name == null || name.isBlank() ? UNNAMED_NODE : name,
                    valueOf(row.getPending())));
        }
        return List.copyOf(result);
    }

    /**
     * 查询结果兜底空表（MyBatis 正常返回空 List，这里防御 mock 与异常分支）
     *
     * @param rows 查询结果
     * @param <T>  行类型
     * @return 非空列表
     */
    private <T> List<T> safe(List<T> rows) {
        return rows == null ? List.of() : rows;
    }

    /**
     * 时间窗参数是否不可用
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻
     * @param to       截止时刻
     * @return 任一缺失或窗口为空时返回 true
     */
    private boolean invalidWindow(Long tenantId, LocalDateTime from, LocalDateTime to) {
        return tenantId == null || from == null || to == null || !from.isBefore(to);
    }

    /**
     * Long 拆箱兜底
     *
     * @param value 可能为 null 的计数
     * @return 非空计数
     */
    private long valueOf(Long value) {
        return value == null ? 0L : value;
    }
}
