package com.bluewhale.job.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.job.entity.JobInfo;
import com.bluewhale.job.entity.JobLog;
import com.bluewhale.job.mapper.JobInfoMapper;
import com.bluewhale.job.mapper.JobLogMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 定时任务调度引擎。
 *
 * <p>基于 {@link ThreadPoolTaskScheduler} + {@link CronTrigger}，不引入新调度依赖。
 * 应用就绪（{@link ApplicationReadyEvent}）时加载 {@code job_info} 中 {@code status=1}
 * 的任务并注册；CRUD 与启停后经 {@link #reschedule(Long)} / {@link #cancel(Long)} 同步调度。</p>
 *
 * <p>执行防重分两层：先经 {@link JobExecutionGuard} 做实例内互斥，再经 Redisson 分布式锁
 * （键 {@code lock:job:execute:{jobId}}）防止多实例重复执行；Redisson 不可用时自动降级为
 * 仅实例内互斥。cron 触发时若上次执行尚未结束，按任务 {@code misfire_policy} 处理：
 * {@code DO_NOTHING} 直接跳过；{@code FIRE_ONCE} / {@code SMART} 登记一次性补跑。</p>
 *
 * <p>每次执行先插入 {@code job_log}（{@code start_time}、{@code status=0}），执行
 * {@link JobHandler}，再回写 {@code end_time} / {@code status}（成功 1 / 失败 -1）/
 * {@code result} / {@code error_msg}，全程 try/catch，异常绝不中断调度线程。</p>
 *
 * <p>失败重试：单次触发内按 {@code max_retry} / {@code retry_interval_seconds} 串行重试，
 * 每次尝试都回写 {@code job_log.retry_count}；重试全程持有实例内互斥与分布式锁，
 * 因此重试期间不会与其他触发并发执行；超过上限后回写失败终态（{@code status=-1}）。
 * 分片：按 {@code shard_total} 串行执行各分片，分片序号写入 {@code job_log.shard_index}，
 * 并经 {@link JobExecutionContext} 透传给执行器；单分片行为与旧逻辑一致。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobScheduler {

    /** 任务状态：运行中 */
    private static final int STATUS_RUNNING = 1;

    /** 执行日志状态：执行中 */
    private static final int LOG_STATUS_RUNNING = 0;

    /** 执行日志状态：成功 */
    private static final int LOG_STATUS_SUCCESS = 1;

    /** 执行日志状态：失败 */
    private static final int LOG_STATUS_FAILURE = -1;

    /** 调度线程池大小 */
    private static final int POOL_SIZE = 4;

    /** 关闭时等待未完成任务的最大秒数 */
    private static final int AWAIT_TERMINATION_SECONDS = 5;

    /** 结果/错误信息落库长度上限（TEXT 字段，落库前截断防膨胀） */
    private static final int MAX_MESSAGE_LENGTH = 2000;

    /** 默认重试次数上限：0 表示不重试 */
    private static final int DEFAULT_MAX_RETRY = 0;

    /** 重试次数上限（防止误配导致线程长期占用） */
    private static final int MAX_RETRY_LIMIT = 5;

    /** 默认重试间隔秒数 */
    private static final int DEFAULT_RETRY_INTERVAL_SECONDS = 60;

    /** 默认分片总数：1 表示不分片 */
    private static final int DEFAULT_SHARD_TOTAL = 1;

    /** 分片总数上限 */
    private static final int MAX_SHARD_TOTAL = 16;

    /** 毫秒换算基数 */
    private static final long MILLIS_PER_SECOND = 1000L;

    /** 分布式锁键前缀 */
    private static final String LOCK_KEY_PREFIX = "lock:job:execute:";

    /** 错过执行策略：放弃错过的执行，不补跑 */
    private static final String MISFIRE_DO_NOTHING = "DO_NOTHING";

    private final JobInfoMapper jobInfoMapper;

    private final JobLogMapper jobLogMapper;

    private final JobHandlerRegistry jobHandlerRegistry;

    private final JobExecutionGuard executionGuard;

    private final ObjectProvider<RedissonClient> redissonClientProvider;

    /** jobId 到已注册调度的映射 */
    private final Map<Long, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();

    /** 调度线程池 */
    private ThreadPoolTaskScheduler taskScheduler;

    /**
     * 初始化调度线程池
     */
    @PostConstruct
    public void init() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(POOL_SIZE);
        scheduler.setThreadNamePrefix("whale-job-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
        scheduler.initialize();
        this.taskScheduler = scheduler;
    }

    /**
     * 关闭调度线程池：等待运行中任务完成后再停止
     */
    @PreDestroy
    public void destroy() {
        if (taskScheduler != null) {
            taskScheduler.destroy();
        }
    }

    /**
     * 应用就绪后加载并注册所有运行中的任务
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadRunningJobs() {
        List<JobInfo> jobs = jobInfoMapper.selectList(
                new LambdaQueryWrapper<JobInfo>().eq(JobInfo::getStatus, STATUS_RUNNING));
        jobs.forEach(this::register);
        log.info("定时任务加载完成：运行中任务共 {} 个", jobs.size());
    }

    /**
     * 重新调度指定任务：先取消旧调度，再按最新定义（若为运行中）重新注册
     *
     * @param jobId 任务ID
     */
    public void reschedule(Long jobId) {
        cancel(jobId);
        JobInfo job = jobInfoMapper.selectById(jobId);
        if (job != null && Integer.valueOf(STATUS_RUNNING).equals(job.getStatus())) {
            register(job);
        }
    }

    /**
     * 取消指定任务的调度
     *
     * @param jobId 任务ID
     */
    public void cancel(Long jobId) {
        ScheduledFuture<?> future = scheduledFutures.remove(jobId);
        if (future != null) {
            future.cancel(false);
            log.info("定时任务已取消: jobId={}", jobId);
        }
    }

    /**
     * 立即触发一次指定任务（异步执行，不阻塞请求线程）
     *
     * @param jobId 任务ID
     * @throws BizException 任务不存在时抛出 {@code NOT_FOUND}
     */
    public void runOnce(Long jobId) {
        JobInfo job = jobInfoMapper.selectById(jobId);
        if (job == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        taskScheduler.execute(() -> execute(job));
    }

    /**
     * 按 cron 表达式注册任务调度；cron 非法或缺失时跳过并告警
     *
     * @param job 任务定义
     */
    private void register(JobInfo job) {
        String cron = job.getCronExpression();
        if (!StringUtils.hasText(cron) || !CronExpression.isValidExpression(cron)) {
            log.warn("任务 cron 表达式非法，跳过注册: jobId={}, cron={}", job.getId(), cron);
            return;
        }
        ScheduledFuture<?> future = taskScheduler.schedule(() -> execute(job), new CronTrigger(cron));
        if (future != null) {
            scheduledFutures.put(job.getId(), future);
            log.info("定时任务已注册: jobId={}, jobName={}, cron={}", job.getId(), job.getJobName(), cron);
        }
    }

    /**
     * 触发一次任务执行：实例内互斥 + 分布式锁，结束后消费待补跑标记。
     *
     * <p>包可见以便单元测试直接驱动执行路径。</p>
     *
     * @param job 任务定义
     */
    void execute(JobInfo job) {
        Long jobId = job.getId();
        if (!executionGuard.tryEnter(jobId)) {
            handleOverlap(job);
            return;
        }
        try {
            runWithDistributedLock(job);
        } finally {
            executionGuard.exit(jobId);
        }
        drainPendingOnce(job);
    }

    /**
     * 在分布式锁保护下执行一次任务；Redisson 不可用时降级为仅实例内互斥。
     *
     * @param job 任务定义
     */
    private void runWithDistributedLock(JobInfo job) {
        RedissonClient client = redissonClientProvider.getIfAvailable();
        if (client == null) {
            doExecute(job);
            return;
        }
        RLock lock = client.getLock(LOCK_KEY_PREFIX + job.getId());
        if (!tryLock(lock)) {
            log.info("未获取分布式锁（可能其他实例执行中），跳过本次执行: jobId={}", job.getId());
            return;
        }
        try {
            doExecute(job);
        } finally {
            unlock(lock);
        }
    }

    /**
     * 非阻塞尝试获取分布式锁。
     *
     * @param lock Redisson 锁
     * @return 获取成功返回 {@code true}；未获取到或被中断返回 {@code false}；Redis 异常时降级放行返回 {@code true}
     */
    private boolean tryLock(RLock lock) {
        try {
            return lock.tryLock(0, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取分布式锁被中断，跳过本次执行: lock={}", lock.getName());
            return false;
        } catch (Exception e) {
            log.warn("分布式锁不可用，降级为实例内互斥执行: lock={}", lock.getName(), e);
            return true;
        }
    }

    /**
     * 释放分布式锁（仅当由当前线程持有时）
     *
     * @param lock Redisson 锁
     */
    private void unlock(RLock lock) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception e) {
            log.warn("释放分布式锁失败: lock={}", lock.getName(), e);
        }
    }

    /**
     * 处理触发重叠（上次执行未结束）：按 misfire 策略跳过或登记一次性补跑
     *
     * @param job 任务定义
     */
    private void handleOverlap(JobInfo job) {
        if (MISFIRE_DO_NOTHING.equalsIgnoreCase(job.getMisfirePolicy())) {
            log.info("任务上次执行未结束，按 DO_NOTHING 策略跳过本次: jobId={}", job.getId());
            return;
        }
        executionGuard.markPendingOnce(job.getId());
        log.info("任务上次执行未结束，登记待补跑一次: jobId={}, misfirePolicy={}",
                job.getId(), job.getMisfirePolicy());
    }

    /**
     * 消费待补跑标记：在当前执行结束后补跑一次；补跑期间再次发生重叠则重新登记，栈深有界
     *
     * @param job 任务定义
     */
    private void drainPendingOnce(JobInfo job) {
        Long jobId = job.getId();
        while (executionGuard.consumePendingOnce(jobId)) {
            if (!executionGuard.tryEnter(jobId)) {
                executionGuard.markPendingOnce(jobId);
                return;
            }
            try {
                runWithDistributedLock(job);
            } finally {
                executionGuard.exit(jobId);
            }
        }
    }

    /**
     * 执行一次任务并落执行日志：按分片总数串行执行各分片，异常只记录，绝不向调度线程抛出
     *
     * <p>包可见以便单元测试直接验证落库与异常处理。</p>
     *
     * @param job 任务定义
     */
    void doExecute(JobInfo job) {
        int shardTotal = resolveShardTotal(job.getShardTotal());
        int failedShards = 0;
        for (int shardIndex = 0; shardIndex < shardTotal; shardIndex++) {
            if (!executeShard(job, shardIndex, shardTotal)) {
                failedShards++;
            }
        }
        if (failedShards > 0) {
            log.warn("任务分片执行完成，存在失败分片: jobId={}, 失败分片数={}/{}",
                    job.getId(), failedShards, shardTotal);
        }
    }

    /**
     * 执行单个分片：失败时按上限与间隔串行重试，每次尝试回写 {@code retry_count}
     *
     * @param job        任务定义
     * @param shardIndex 当前分片序号（从 0 开始）
     * @param shardTotal 分片总数
     * @return 最终成功返回 {@code true}；重试耗尽仍失败返回 {@code false}
     */
    private boolean executeShard(JobInfo job, int shardIndex, int shardTotal) {
        JobLog jobLog = insertRunningLog(job, shardIndex);
        int maxRetry = resolveMaxRetry(job.getMaxRetry());
        long intervalMillis = resolveRetryIntervalSeconds(job.getRetryIntervalSeconds()) * MILLIS_PER_SECOND;
        for (int retryCount = 0; ; retryCount++) {
            try {
                String result = invokeHandler(job, shardIndex, shardTotal, retryCount);
                markSuccess(jobLog, result, retryCount);
                return true;
            } catch (Exception e) {
                if (retryCount >= maxRetry) {
                    markFailure(jobLog, e, retryCount);
                    log.warn("任务执行失败且重试已耗尽: jobId={}, jobName={}, shardIndex={}/{}, retryCount={}",
                            job.getId(), job.getJobName(), shardIndex, shardTotal, retryCount, e);
                    return false;
                }
                jobLog.setRetryCount(retryCount + 1);
                jobLogMapper.updateById(jobLog);
                log.warn("任务执行失败，{} 秒后进行第 {} 次重试: jobId={}, shardIndex={}/{}",
                        intervalMillis / MILLIS_PER_SECOND, retryCount + 1, job.getId(), shardIndex, shardTotal, e);
                if (!awaitRetry(intervalMillis)) {
                    markFailure(jobLog, e, retryCount + 1);
                    return false;
                }
            }
        }
    }

    /**
     * 插入执行中日志（{@code status=0}）
     *
     * @param job        任务定义
     * @param shardIndex 当前分片序号
     * @return 已落库的执行日志实体（主键已回填）
     */
    private JobLog insertRunningLog(JobInfo job, int shardIndex) {
        JobLog jobLog = new JobLog();
        jobLog.setJobId(job.getId());
        jobLog.setJobName(job.getJobName());
        jobLog.setHandlerBean(job.getHandlerBean());
        jobLog.setStartTime(LocalDateTime.now());
        jobLog.setStatus(LOG_STATUS_RUNNING);
        jobLog.setRetryCount(0);
        jobLog.setShardIndex(shardIndex);
        jobLogMapper.insert(jobLog);
        return jobLog;
    }

    /**
     * 调用执行器：将分片与重试信息封装为上下文透传
     *
     * @param job        任务定义
     * @param shardIndex 当前分片序号
     * @param shardTotal 分片总数
     * @param retryCount 本次执行已重试次数
     * @return 执行结果摘要
     * @throws Exception 执行器执行失败时抛出
     */
    private String invokeHandler(JobInfo job, int shardIndex, int shardTotal, int retryCount) throws Exception {
        JobHandler handler = jobHandlerRegistry.resolve(job.getHandlerBean());
        JobExecutionContext context = new JobExecutionContext(job.getId(), job.getJobName(),
                job.getHandlerParam(), shardIndex, shardTotal, retryCount);
        return handler.executeWithContext(context);
    }

    /**
     * 回写成功终态
     *
     * @param jobLog     执行日志实体
     * @param result     执行结果摘要
     * @param retryCount 本次执行已重试次数
     */
    private void markSuccess(JobLog jobLog, String result, int retryCount) {
        jobLog.setEndTime(LocalDateTime.now());
        jobLog.setStatus(LOG_STATUS_SUCCESS);
        jobLog.setRetryCount(retryCount);
        jobLog.setResult(truncate(result));
        jobLogMapper.updateById(jobLog);
    }

    /**
     * 回写失败终态（重试耗尽或被中断）
     *
     * @param jobLog     执行日志实体
     * @param e          失败异常
     * @param retryCount 本次执行已重试次数
     */
    private void markFailure(JobLog jobLog, Exception e, int retryCount) {
        jobLog.setEndTime(LocalDateTime.now());
        jobLog.setStatus(LOG_STATUS_FAILURE);
        jobLog.setRetryCount(retryCount);
        jobLog.setErrorMsg(truncate(e.getClass().getSimpleName()
                + (e.getMessage() == null ? "" : ": " + e.getMessage())));
        jobLogMapper.updateById(jobLog);
    }

    /**
     * 等待重试间隔
     *
     * @param intervalMillis 重试间隔毫秒数
     * @return 正常等待完成返回 {@code true}；等待被中断返回 {@code false}（终止重试）
     */
    private static boolean awaitRetry(long intervalMillis) {
        if (intervalMillis <= 0) {
            return true;
        }
        try {
            Thread.sleep(intervalMillis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("重试等待被中断，终止本次重试", e);
            return false;
        }
    }

    /**
     * 归一化重试次数上限
     *
     * @param maxRetry 配置值（可为空）
     * @return 空或负数返回 0，超过上限按上限截断
     */
    private static int resolveMaxRetry(Integer maxRetry) {
        if (maxRetry == null || maxRetry < 0) {
            return DEFAULT_MAX_RETRY;
        }
        return Math.min(maxRetry, MAX_RETRY_LIMIT);
    }

    /**
     * 归一化重试间隔秒数
     *
     * @param retryIntervalSeconds 配置值（可为空）
     * @return 空返回默认 60 秒，负数按 0 处理
     */
    private static int resolveRetryIntervalSeconds(Integer retryIntervalSeconds) {
        if (retryIntervalSeconds == null) {
            return DEFAULT_RETRY_INTERVAL_SECONDS;
        }
        return Math.max(retryIntervalSeconds, 0);
    }

    /**
     * 归一化分片总数
     *
     * @param shardTotal 配置值（可为空）
     * @return 空或小于 1 返回 1，超过上限按上限截断
     */
    private static int resolveShardTotal(Integer shardTotal) {
        if (shardTotal == null || shardTotal < DEFAULT_SHARD_TOTAL) {
            return DEFAULT_SHARD_TOTAL;
        }
        return Math.min(shardTotal, MAX_SHARD_TOTAL);
    }

    /**
     * 按上限截断文本
     *
     * @param text 原始文本
     * @return 截断后的文本（入参为空返回 {@code null}）
     */
    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= MAX_MESSAGE_LENGTH ? text : text.substring(0, MAX_MESSAGE_LENGTH);
    }
}
