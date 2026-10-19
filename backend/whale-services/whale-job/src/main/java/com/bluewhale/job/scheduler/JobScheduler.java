package com.bluewhale.job.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.job.entity.JobInfo;
import com.bluewhale.job.entity.JobLog;
import com.bluewhale.job.mapper.JobInfoMapper;
import com.bluewhale.job.mapper.JobLogMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
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

/**
 * 定时任务调度引擎。
 *
 * <p>基于 {@link ThreadPoolTaskScheduler} + {@link CronTrigger}，不引入新依赖。
 * 应用就绪（{@link ApplicationReadyEvent}）时加载 {@code job_info} 中 {@code status=1}
 * 的任务并注册；CRUD 与启停后经 {@link #reschedule(Long)} / {@link #cancel(Long)} 同步调度。
 * 每次触发执行：先插入 {@code job_log}（{@code start_time}、{@code status=0}），执行
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

    private final JobInfoMapper jobInfoMapper;

    private final JobLogMapper jobLogMapper;

    private final ApplicationContext applicationContext;

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
     * 立即执行一次指定任务（同步执行，便于调用方在返回前观察到执行日志）
     *
     * @param jobId 任务ID
     * @throws BizException 任务不存在时抛出 {@code NOT_FOUND}
     */
    public void runOnce(Long jobId) {
        JobInfo job = jobInfoMapper.selectById(jobId);
        if (job == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        execute(job);
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
     * 执行一次任务并落执行日志：异常只记录，绝不向调度线程抛出
     *
     * @param job 任务定义
     */
    private void execute(JobInfo job) {
        JobLog jobLog = new JobLog();
        jobLog.setJobId(job.getId());
        jobLog.setJobName(job.getJobName());
        jobLog.setHandlerBean(job.getHandlerBean());
        jobLog.setStartTime(LocalDateTime.now());
        jobLog.setStatus(LOG_STATUS_RUNNING);
        jobLogMapper.insert(jobLog);
        try {
            JobHandler handler = resolveHandler(job.getHandlerBean());
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
     * 按 Bean 名称从容器解析执行器
     *
     * @param beanName 执行器 Bean 名称
     * @return 执行器
     * @throws BizException Bean 缺失或未实现 {@link JobHandler} 时抛出
     */
    private JobHandler resolveHandler(String beanName) {
        if (!StringUtils.hasText(beanName)) {
            throw new BizException("执行器 Bean 名称为空");
        }
        Object bean = applicationContext.getBean(beanName);
        if (!(bean instanceof JobHandler handler)) {
            throw new BizException("Bean 未实现 JobHandler: " + beanName);
        }
        return handler;
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
