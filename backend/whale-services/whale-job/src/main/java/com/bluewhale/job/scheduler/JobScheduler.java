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
     * 执行一次任务并落执行日志：异常只记录，绝不向调度线程抛出
     *
     * <p>包可见以便单元测试直接验证落库与异常处理。</p>
     *
     * @param job 任务定义
     */
    void doExecute(JobInfo job) {
        JobLog jobLog = new JobLog();
        jobLog.setJobId(job.getId());
        jobLog.setJobName(job.getJobName());
        jobLog.setHandlerBean(job.getHandlerBean());
        jobLog.setStartTime(LocalDateTime.now());
        jobLog.setStatus(LOG_STATUS_RUNNING);
        jobLogMapper.insert(jobLog);
        try {
            JobHandler handler = jobHandlerRegistry.resolve(job.getHandlerBean());
            String result = handler.execute(job.getHandlerParam());
            jobLog.setEndTime(LocalDateTime.now());
            jobLog.setStatus(LOG_STATUS_SUCCESS);
            jobLog.setResult(truncate(result));
        } catch (Exception e) {
            jobLog.setEndTime(LocalDateTime.now());
            jobLog.setStatus(LOG_STATUS_FAILURE);
            jobLog.setErrorMsg(truncate(e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage())));
            log.warn("任务执行失败: jobId={}, jobName={}", job.getId(), job.getJobName(), e);
        } finally {
            jobLogMapper.updateById(jobLog);
        }
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
