package com.bluewhale.job.service.impl;

import com.bluewhale.job.mapper.JobInfoMapper;
import com.bluewhale.job.mapper.JobLogMapper;
import com.bluewhale.job.po.stat.JobCountPo;
import com.bluewhale.job.po.stat.JobDayRunPo;
import com.bluewhale.job.po.stat.JobFailTopPo;
import com.bluewhale.job.po.stat.JobRunStatPo;
import com.bluewhale.job.service.JobScheduleStatQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link JobScheduleStatQuery} 的 MyBatis 实现。
 *
 * <p>所有查询显式带 {@code tenant_id}，时间一律写成 {@code column >= ? AND column < ?}，
 * 函数不套在条件列上——{@code DATE(start_time) = ?} 会让 {@code idx_start_time} 直接失效。</p>
 *
 * <p>空结果折算成 0 而不是 null：概览是给人看的，"今日执行 null 次"比 0 更像故障。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class JobScheduleStatQueryImpl implements JobScheduleStatQuery {

    /** 任务名缺失时的兜底展示名（历史数据允许为空） */
    private static final String UNNAMED_JOB = "未命名任务";

    private final JobInfoMapper jobInfoMapper;

    private final JobLogMapper jobLogMapper;

    @Override
    public JobCount jobCount(Long tenantId) {
        if (tenantId == null) {
            return new JobCount(0L, 0L);
        }
        JobCountPo row = jobInfoMapper.countJobs(tenantId);
        if (row == null) {
            return new JobCount(0L, 0L);
        }
        return new JobCount(valueOf(row.getJobs()), valueOf(row.getRunning()));
    }

    @Override
    public RunStat runsBetween(Long tenantId, LocalDateTime from, LocalDateTime to) {
        if (invalidWindow(tenantId, from, to)) {
            return new RunStat(0L, 0L, 0L);
        }
        JobRunStatPo row = jobLogMapper.sumRunsBetween(tenantId, from, to);
        if (row == null) {
            return new RunStat(0L, 0L, 0L);
        }
        return new RunStat(valueOf(row.getTotalRuns()), valueOf(row.getSuccessRuns()),
                valueOf(row.getFailureRuns()));
    }

    @Override
    public long failedJobCount(Long tenantId, LocalDateTime from, LocalDateTime to) {
        if (invalidWindow(tenantId, from, to)) {
            return 0L;
        }
        return jobLogMapper.countFailedJobs(tenantId, from, to);
    }

    @Override
    public long silentRunning(Long tenantId, LocalDateTime since) {
        if (tenantId == null || since == null) {
            return 0L;
        }
        return jobInfoMapper.countSilentRunning(tenantId, since);
    }

    @Override
    public List<DayRuns> dailyRuns(Long tenantId, LocalDateTime from, LocalDateTime to) {
        if (invalidWindow(tenantId, from, to)) {
            return List.of();
        }
        List<JobDayRunPo> rows = jobLogMapper.countRunsByDay(tenantId, from, to);
        List<DayRuns> result = new ArrayList<>(rows.size());
        for (JobDayRunPo row : rows) {
            if (row.getDay() == null) {
                continue;
            }
            result.add(new DayRuns(row.getDay(), valueOf(row.getRuns())));
        }
        return List.copyOf(result);
    }

    @Override
    public List<FailTop> failTop(Long tenantId, LocalDateTime from, LocalDateTime to, int limit) {
        if (invalidWindow(tenantId, from, to) || limit <= 0) {
            return List.of();
        }
        List<JobFailTopPo> rows = jobLogMapper.topFailedJobs(tenantId, from, to, limit);
        List<FailTop> result = new ArrayList<>(rows.size());
        for (JobFailTopPo row : rows) {
            String name = row.getJobName();
            result.add(new FailTop(name == null || name.isBlank() ? UNNAMED_JOB : name,
                    valueOf(row.getFailures())));
        }
        return List.copyOf(result);
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
