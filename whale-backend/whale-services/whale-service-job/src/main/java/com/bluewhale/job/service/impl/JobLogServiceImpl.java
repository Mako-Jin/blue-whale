package com.bluewhale.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.job.converter.JobConverter;
import com.bluewhale.job.dto.JobLogQueryRequest;
import com.bluewhale.job.dto.JobLogStatsQueryRequest;
import com.bluewhale.job.po.JobInfoPo;
import com.bluewhale.job.po.JobLogPo;
import com.bluewhale.job.mapper.JobInfoMapper;
import com.bluewhale.job.mapper.JobLogMapper;
import com.bluewhale.job.scheduler.handler.ExecutorLogClient;
import com.bluewhale.job.service.JobLogService;
import com.bluewhale.job.vo.ExecutorLogVO;
import com.bluewhale.job.vo.JobDurationTopVO;
import com.bluewhale.job.vo.JobFailureTopVO;
import com.bluewhale.job.vo.JobLogDailyStatVO;
import com.bluewhale.job.vo.JobLogStatsVO;
import com.bluewhale.job.vo.JobLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 定时任务执行日志服务实现（写入由调度引擎 {@code JobScheduler} 完成，本服务只读）
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class JobLogServiceImpl implements JobLogService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 统计默认窗口天数 */
    private static final long DEFAULT_STATS_DAYS = 7L;

    /** 统计最大窗口天数 */
    private static final long MAX_STATS_DAYS = 90L;

    /** 单次统计最多取用的明细行数，超出置 sampled */
    private static final int MAX_SAMPLE_ROWS = 100000;

    /** TopN 条数 */
    private static final int TOP_N = 10;

    /** 执行日志状态：成功 */
    private static final int LOG_STATUS_SUCCESS = 1;

    /** 执行日志状态：失败 */
    private static final int LOG_STATUS_FAILURE = -1;

    /** P95 百分位 */
    private static final double P95 = 0.95D;

    private final JobLogMapper jobLogMapper;

    private final JobInfoMapper jobInfoMapper;

    private final ExecutorLogClient executorLogClient;

    @Override
    public PageResult<JobLogVO> page(JobLogQueryRequest request) {
        LambdaQueryWrapper<JobLogPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(request.getJobId() != null, JobLogPo::getJobId, request.getJobId());
        wrapper.eq(request.getStatus() != null, JobLogPo::getStatus, request.getStatus());
        wrapper.orderByDesc(JobLogPo::getStartTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<JobLogPo> page = jobLogMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<JobLogVO> records = page.getRecords().stream().map(JobLogServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public ExecutorLogVO executorLog(Long logId) {
        JobLogPo jobLog = jobLogMapper.selectById(logId);
        if (jobLog == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "执行流水不存在: " + logId);
        }
        JobInfoPo job = jobInfoMapper.selectById(jobLog.getJobId());
        ExecutorLogVO vo = new ExecutorLogVO();
        vo.setLogId(logId);
        String jobCode = job == null ? null : job.getJobCode();
        String executorApp = job == null ? null : job.getExecutorApp();
        vo.setJobCode(jobCode);
        vo.setExecutorApp(executorApp);
        if (jobCode == null) {
            vo.setNote("任务定义已删除或未配置 job_code，无法定位执行器日志");
            return vo;
        }
        ExecutorLogClient.Fetched fetched = executorLogClient.fetch(
                job.getTenantId(), executorApp, jobCode, jobLog.getExecutorAddress());
        vo.setAddress(fetched.address());
        vo.setFound(fetched.found());
        vo.setContent(fetched.content());
        vo.setNote(fetched.note());
        return vo;
    }

    /**
     * 执行日志实体转视图对象
     *
     * @param jobLog 执行日志实体
     * @return 执行日志视图对象
     */
    private static JobLogVO toVo(JobLogPo jobLog) {
        return JobConverter.INSTANCE.toLogVo(jobLog);
    }

    @Override
    public JobLogStatsVO stats(JobLogStatsQueryRequest request) {
        LocalDateTime to = request.getTo() == null ? LocalDateTime.now() : request.getTo();
        LocalDateTime from = request.getFrom() == null ? to.minusDays(DEFAULT_STATS_DAYS) : request.getFrom();
        if (!from.isBefore(to)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "统计开始时间必须早于结束时间");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_STATS_DAYS) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "统计时间跨度不能超过 " + MAX_STATS_DAYS + " 天");
        }
        JobLogStatsVO vo = new JobLogStatsVO();
        vo.setFrom(from);
        vo.setTo(to);
        vo.setDaily(List.of());
        vo.setFailureTop(List.of());
        vo.setDurationTop(List.of());

        List<Long> scopedJobIds = resolveScopedJobIds(request.getJobGroup());
        if (scopedJobIds != null && scopedJobIds.isEmpty()) {
            return vo;
        }
        LambdaQueryWrapper<JobLogPo> wrapper = new LambdaQueryWrapper<JobLogPo>()
                .select(JobLogPo::getJobId, JobLogPo::getJobName, JobLogPo::getStatus,
                        JobLogPo::getStartTime, JobLogPo::getEndTime)
                .in(JobLogPo::getStatus, LOG_STATUS_SUCCESS, LOG_STATUS_FAILURE)
                .ge(JobLogPo::getStartTime, from)
                .le(JobLogPo::getStartTime, to)
                .eq(request.getJobId() != null, JobLogPo::getJobId, request.getJobId())
                .in(scopedJobIds != null, JobLogPo::getJobId, scopedJobIds)
                .orderByAsc(JobLogPo::getStartTime)
                .last("LIMIT " + (MAX_SAMPLE_ROWS + 1));
        List<JobLogPo> rows = jobLogMapper.selectList(wrapper);
        boolean sampled = rows.size() > MAX_SAMPLE_ROWS;
        if (sampled) {
            rows = rows.subList(0, MAX_SAMPLE_ROWS);
        }
        if (rows.isEmpty()) {
            return vo;
        }
        vo.setSampled(sampled);
        vo.setTotalCount(rows.size());
        vo.setDaily(buildDaily(rows));
        if (request.getJobId() == null) {
            vo.setFailureTop(buildFailureTop(rows));
            vo.setDurationTop(buildDurationTop(rows));
        }
        return vo;
    }

    /**
     * 解析分组下的任务ID集合
     *
     * @param jobGroup 任务分组
     * @return 分组为空返回 {@code null}（不过滤）；分组下无任务返回空列表
     */
    private List<Long> resolveScopedJobIds(String jobGroup) {
        if (!StringUtils.hasText(jobGroup)) {
            return null;
        }
        return jobInfoMapper.selectList(new LambdaQueryWrapper<JobInfoPo>()
                        .select(JobInfoPo::getId)
                        .eq(JobInfoPo::getJobGroup, jobGroup)).stream()
                .map(JobInfoPo::getId)
                .toList();
    }

    /**
     * 按天聚合成功/失败数、平均耗时与 P95
     *
     * @param rows 已完成执行日志
     * @return 日期升序的每日统计
     */
    private static List<JobLogDailyStatVO> buildDaily(List<JobLogPo> rows) {
        Map<LocalDate, DayBucket> buckets = new TreeMap<>();
        for (JobLogPo row : rows) {
            DayBucket bucket = buckets.computeIfAbsent(row.getStartTime().toLocalDate(), key -> new DayBucket());
            if (row.getStatus() != null && row.getStatus() == LOG_STATUS_SUCCESS) {
                bucket.successCount++;
            } else {
                bucket.failureCount++;
            }
            if (row.getEndTime() != null) {
                bucket.durations.add(ChronoUnit.MILLIS.between(row.getStartTime(), row.getEndTime()));
            }
        }
        List<JobLogDailyStatVO> daily = new ArrayList<>(buckets.size());
        buckets.forEach((date, bucket) -> {
            JobLogDailyStatVO stat = new JobLogDailyStatVO();
            stat.setDate(date);
            stat.setSuccessCount(bucket.successCount);
            stat.setFailureCount(bucket.failureCount);
            long total = bucket.successCount + bucket.failureCount;
            long sum = bucket.durations.stream().mapToLong(Long::longValue).sum();
            stat.setAvgDurationMillis(total == 0 ? 0 : sum / total);
            stat.setP95DurationMillis(percentile(bucket.durations, P95));
            daily.add(stat);
        });
        return daily;
    }

    /**
     * 按任务聚合失败次数并取 TopN
     *
     * @param rows 已完成执行日志
     * @return 失败次数降序的前 10 条
     */
    private static List<JobFailureTopVO> buildFailureTop(List<JobLogPo> rows) {
        Map<Long, JobAccumulator> accumulators = accumulateByJob(rows);
        return accumulators.values().stream()
                .filter(accumulator -> accumulator.failures > 0)
                .sorted(Comparator.comparingLong((JobAccumulator item) -> item.failures).reversed()
                        .thenComparingLong(item -> item.jobId))
                .limit(TOP_N)
                .map(accumulator -> {
                    JobFailureTopVO item = new JobFailureTopVO();
                    item.setJobId(accumulator.jobId);
                    item.setJobName(accumulator.jobName);
                    item.setFailureCount(accumulator.failures);
                    return item;
                })
                .toList();
    }

    /**
     * 按任务聚合平均耗时并取 TopN
     *
     * @param rows 已完成执行日志
     * @return 平均耗时降序的前 10 条
     */
    private static List<JobDurationTopVO> buildDurationTop(List<JobLogPo> rows) {
        Map<Long, JobAccumulator> accumulators = accumulateByJob(rows);
        return accumulators.values().stream()
                .filter(accumulator -> accumulator.executions > 0)
                .sorted(Comparator.comparingLong(JobAccumulator::avgDurationMillis).reversed()
                        .thenComparingLong(item -> item.jobId))
                .limit(TOP_N)
                .map(accumulator -> {
                    JobDurationTopVO item = new JobDurationTopVO();
                    item.setJobId(accumulator.jobId);
                    item.setJobName(accumulator.jobName);
                    item.setAvgDurationMillis(accumulator.avgDurationMillis());
                    item.setExecutionCount(accumulator.executions);
                    return item;
                })
                .toList();
    }

    /**
     * 按任务累计执行次数、失败次数与耗时总和
     *
     * @param rows 已完成执行日志
     * @return jobId → 聚合结果（保持首次出现顺序）
     */
    private static Map<Long, JobAccumulator> accumulateByJob(List<JobLogPo> rows) {
        Map<Long, JobAccumulator> accumulators = new LinkedHashMap<>();
        for (JobLogPo row : rows) {
            JobAccumulator accumulator = accumulators.computeIfAbsent(row.getJobId(),
                    key -> new JobAccumulator(key, row.getJobName()));
            accumulator.executions++;
            accumulator.jobName = row.getJobName();
            if (row.getStatus() != null && row.getStatus() == LOG_STATUS_FAILURE) {
                accumulator.failures++;
            }
            if (row.getEndTime() != null) {
                accumulator.durationSum += ChronoUnit.MILLIS.between(row.getStartTime(), row.getEndTime());
            }
        }
        return accumulators;
    }

    /**
     * 离散百分位：升序第 {@code ceil(n × p)} 个，空集合返回 0
     *
     * @param durations 耗时毫秒列表
     * @param percentile 百分位（0~1）
     * @return 百分位耗时毫秒数
     */
    private static long percentile(List<Long> durations, double percentile) {
        if (durations.isEmpty()) {
            return 0L;
        }
        List<Long> sorted = new ArrayList<>(durations);
        sorted.sort(Long::compareTo);
        int index = (int) Math.ceil(sorted.size() * percentile) - 1;
        return sorted.get(Math.max(index, 0));
    }

    /**
     * 单日统计桶
     */
    private static final class DayBucket {

        /** 成功数 */
        private long successCount;

        /** 失败数 */
        private long failureCount;

        /** 已完成执行的耗时列表（毫秒） */
        private final List<Long> durations = new ArrayList<>();
    }

    /**
     * 单任务统计累加器
     */
    private static final class JobAccumulator {

        /** 任务ID */
        private final Long jobId;

        /** 任务名称（取窗口内最后一条日志的冗余名称） */
        private String jobName;

        /** 执行次数 */
        private long executions;

        /** 失败次数 */
        private long failures;

        /** 耗时总和（毫秒） */
        private long durationSum;

        /**
         * 构造任务累加器
         *
         * @param jobId 任务ID
         * @param jobName 任务名称
         */
        private JobAccumulator(Long jobId, String jobName) {
            this.jobId = jobId;
            this.jobName = jobName;
        }

        /**
         * 平均耗时毫秒数
         *
         * @return 平均耗时；无执行返回 0
         */
        private long avgDurationMillis() {
            return executions == 0 ? 0L : durationSum / executions;
        }
    }
}
