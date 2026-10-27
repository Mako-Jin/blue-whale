package com.bluewhale.job.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.job.alert.JobAlertNotifier;
import com.bluewhale.job.entity.JobDependency;
import com.bluewhale.job.entity.JobInfo;
import com.bluewhale.job.entity.JobLog;
import com.bluewhale.job.mapper.JobDependencyMapper;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
 * <p>依赖编排：根触发（cron / 手动）生成 {@code runId}；存在上游依赖的任务不注册自身 cron，
 * 仅在上游任务全部分片成功且其所有上游在同一 {@code runId} 下均成功后，以 {@code CHAIN}
 * 触发类型提交下游执行，链深度上限 32。无依赖任务的 cron / 重试 / 分片 / misfire / 锁行为保持不变。</p>
 *
 * <p>多实例一致性：任务定义变更、启停与依赖调整后经 {@link JobScheduleBroadcaster} 广播失效消息，
 * 其余实例收到后按同一语义重新注册或取消本地调度；广播不可用时保持单实例语义。</p>
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

    /** 触发类型：cron 定时 */
    private static final String TRIGGER_CRON = "CRON";

    /** 触发类型：手动 */
    private static final String TRIGGER_MANUAL = "MANUAL";

    /** 触发类型：依赖链 */
    private static final String TRIGGER_CHAIN = "CHAIN";

    /** 依赖链深度上限：超限后停止向下游传递 */
    private static final int MAX_CHAIN_DEPTH = 32;

    private final JobInfoMapper jobInfoMapper;

    private final JobLogMapper jobLogMapper;

    private final JobDependencyMapper jobDependencyMapper;

    private final JobHandlerRegistry jobHandlerRegistry;

    private final JobExecutionGuard executionGuard;

    private final ObjectProvider<RedissonClient> redissonClientProvider;

    private final JobAlertNotifier jobAlertNotifier;

    private final JobScheduleBroadcaster jobScheduleBroadcaster;

    /** jobId 到已注册调度的映射 */
    private final Map<Long, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();

    /** jobId 到本地已注册调度签名（cron）的映射，用于断连重对齐时判断是否需要重注册 */
    private final Map<Long, String> scheduledSignatures = new ConcurrentHashMap<>();

    /** 调度线程池 */
    private ThreadPoolTaskScheduler taskScheduler;

    /**
     * 初始化调度线程池，并注册多实例失效广播的本地消费者
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
        jobScheduleBroadcaster.registerConsumer(this::applyBroadcast);
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
     * 重新调度指定任务：先取消旧调度，再按最新定义（若为运行中）重新注册，并广播至其他实例
     *
     * @param jobId 任务ID
     */
    public void reschedule(Long jobId) {
        rescheduleLocal(jobId);
        jobScheduleBroadcaster.broadcastReschedule(jobId);
    }

    /**
     * 重新调度指定任务（仅本实例）
     *
     * @param jobId 任务ID
     */
    private void rescheduleLocal(Long jobId) {
        cancelLocal(jobId);
        JobInfo job = jobInfoMapper.selectById(jobId);
        if (job != null && Integer.valueOf(STATUS_RUNNING).equals(job.getStatus())) {
            register(job);
        }
    }

    /**
     * 取消指定任务的调度，并广播至其他实例
     *
     * @param jobId 任务ID
     */
    public void cancel(Long jobId) {
        cancelLocal(jobId);
        jobScheduleBroadcaster.broadcastCancel(jobId);
    }

    /**
     * 取消指定任务的调度（仅本实例）
     *
     * @param jobId 任务ID
     */
    private void cancelLocal(Long jobId) {
        ScheduledFuture<?> future = scheduledFutures.remove(jobId);
        scheduledSignatures.remove(jobId);
        if (future != null) {
            future.cancel(false);
            log.info("定时任务已取消: jobId={}", jobId);
        }
    }

    /**
     * 应用其他实例广播的调度变更
     *
     * @param jobId 任务ID
     * @param reschedule 是否重新注册
     */
    void applyBroadcast(Long jobId, boolean reschedule) {
        if (reschedule) {
            rescheduleLocal(jobId);
        } else {
            cancelLocal(jobId);
        }
    }

    /**
     * 与数据库全量重对齐本地调度（F-02）。
     *
     * <p>Redis Pub/Sub 断连或消息丢失时，其他实例的变更不会到达本实例。本方法按数据库中的
     * 运行中任务列表：取消已停用/已删除任务的本地调度；对 cron 已变化或缺少依赖关系变化的任务
     * 重新注册。由 {@link JobScheduleReconcileJob} 周期执行，异常只记录不抛出。</p>
     */
    public void reconcileWithDatabase() {
        List<JobInfo> runningJobs = jobInfoMapper.selectList(
                new LambdaQueryWrapper<JobInfo>().eq(JobInfo::getStatus, STATUS_RUNNING));
        Set<Long> runningIds = new HashSet<>();
        for (JobInfo job : runningJobs) {
            if (job.getId() != null) {
                runningIds.add(job.getId());
            }
        }
        int canceled = 0;
        for (Long scheduledId : List.copyOf(scheduledFutures.keySet())) {
            if (!runningIds.contains(scheduledId)) {
                cancelLocal(scheduledId);
                canceled++;
            }
        }
        int rescheduled = 0;
        for (JobInfo job : runningJobs) {
            Long jobId = job.getId();
            if (jobId == null) {
                continue;
            }
            if (hasUpstreamDependency(jobId)) {
                if (scheduledFutures.containsKey(jobId)) {
                    cancelLocal(jobId);
                    canceled++;
                }
                continue;
            }
            if (!signature(job).equals(scheduledSignatures.get(jobId))) {
                cancelLocal(jobId);
                register(job);
                rescheduled++;
            }
        }
        log.info("定时任务调度的数据库重对齐完成: running={}, canceled={}, rescheduled={}",
                runningJobs.size(), canceled, rescheduled);
    }

    /**
     * 本地已注册调度的任务ID集合（供满足性测试与运维诊断使用）
     *
     * @return 任务ID集合快照
     */
    Set<Long> scheduledJobIds() {
        return Set.copyOf(scheduledFutures.keySet());
    }

    /**
     * 调度签名：仅包含影响调度注册的 cron 表达式
     *
     * @param job 任务定义
     * @return 调度签名
     */
    private static String signature(JobInfo job) {
        return job.getCronExpression() == null ? "" : job.getCronExpression();
    }

    /**
     * 立即触发一次指定任务（异步执行，不阻塞请求线程）。
     *
     * <p>手动触发忽略上游门禁，直接以 {@code MANUAL} 类型执行并生成新的 {@code runId}。</p>
     *
     * @param jobId 任务ID
     * @throws BizException 任务不存在时抛出 {@code NOT_FOUND}
     */
    public void runOnce(Long jobId) {
        JobInfo job = jobInfoMapper.selectById(jobId);
        if (job == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        String runId = newRunId();
        taskScheduler.execute(() -> executeWithRun(job, runId, TRIGGER_MANUAL, 0));
    }

    /**
     * 按 cron 表达式注册任务调度；cron 非法或缺失时跳过并告警。
     *
     * <p>存在上游依赖的任务不注册自身 cron，仅由上游成功执行后链式触发。</p>
     *
     * @param job 任务定义
     */
    private void register(JobInfo job) {
        if (hasUpstreamDependency(job.getId())) {
            log.info("任务存在上游依赖，跳过 cron 注册，仅由上游成功后链式触发: jobId={}, jobName={}",
                    job.getId(), job.getJobName());
            return;
        }
        String cron = job.getCronExpression();
        scheduledSignatures.put(job.getId(), signature(job));
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
     * 判断任务是否存在上游依赖
     *
     * @param jobId 任务ID
     * @return 存在至少一条上游依赖返回 {@code true}
     */
    private boolean hasUpstreamDependency(Long jobId) {
        Long count = jobDependencyMapper.selectCount(new LambdaQueryWrapper<JobDependency>()
                .eq(JobDependency::getJobId, jobId));
        return count != null && count > 0;
    }

    /**
     * 触发一次任务执行（cron 根触发）：生成 {@code runId}，触发类型为 {@code CRON}。
     *
     * <p>包可见以便单元测试直接驱动执行路径。</p>
     *
     * @param job 任务定义
     */
    void execute(JobInfo job) {
        executeWithRun(job, newRunId(), TRIGGER_CRON, 0);
    }

    /**
     * 在指定运行上下文下触发一次任务执行：实例内互斥 + 分布式锁，结束后消费待补跑标记。
     *
     * @param job 任务定义
     * @param runId 执行批次ID
     * @param triggerType 触发类型
     * @param depth 依赖链深度（根触发为 0）
     */
    private void executeWithRun(JobInfo job, String runId, String triggerType, int depth) {
        Long jobId = job.getId();
        Long previousTenant = TenantContext.get();
        TenantContext.set(job.getTenantId() == null ? TenantContext.DEFAULT_TENANT_ID : job.getTenantId());
        try {
            if (!executionGuard.tryEnter(jobId)) {
                handleOverlap(job);
                return;
            }
            try {
                runWithDistributedLock(job, runId, triggerType, depth);
            } finally {
                executionGuard.exit(jobId);
            }
            drainPendingOnce(job, runId, triggerType, depth);
        } finally {
            TenantContext.set(previousTenant);
        }
    }

    /**
     * 在分布式锁保护下执行一次任务；Redisson 不可用时降级为仅实例内互斥。
     *
     * @param job 任务定义
     * @param runId 执行批次ID
     * @param triggerType 触发类型
     * @param depth 依赖链深度
     */
    private void runWithDistributedLock(JobInfo job, String runId, String triggerType, int depth) {
        RedissonClient client = redissonClientProvider.getIfAvailable();
        if (client == null) {
            doExecute(job, runId, triggerType, depth);
            return;
        }
        RLock lock = client.getLock(LOCK_KEY_PREFIX + job.getId());
        if (!tryLock(lock)) {
            log.info("未获取分布式锁（可能其他实例执行中），跳过本次执行: jobId={}", job.getId());
            return;
        }
        try {
            doExecute(job, runId, triggerType, depth);
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
     * @param runId 执行批次ID
     * @param triggerType 触发类型
     * @param depth 依赖链深度
     */
    private void drainPendingOnce(JobInfo job, String runId, String triggerType, int depth) {
        Long jobId = job.getId();
        while (executionGuard.consumePendingOnce(jobId)) {
            if (!executionGuard.tryEnter(jobId)) {
                executionGuard.markPendingOnce(jobId);
                return;
            }
            try {
                runWithDistributedLock(job, runId, triggerType, depth);
            } finally {
                executionGuard.exit(jobId);
            }
        }
    }

    /**
     * 执行一次任务并落执行日志：生成 {@code runId}，触发类型为 {@code CRON}。
     *
     * <p>包可见以便单元测试直接验证落库与异常处理。</p>
     *
     * @param job 任务定义
     */
    void doExecute(JobInfo job) {
        doExecute(job, newRunId(), TRIGGER_CRON, 0);
    }

    /**
     * 在指定运行上下文下执行一次任务并落执行日志：按分片总数串行执行各分片，
     * 全部分片成功后触发下游依赖任务，异常只记录，绝不向调度线程抛出。
     *
     * <p>包可见以便单元测试直接验证落库与依赖编排。</p>
     *
     * @param job 任务定义
     * @param runId 执行批次ID
     * @param triggerType 触发类型
     * @param depth 依赖链深度
     */
    void doExecute(JobInfo job, String runId, String triggerType, int depth) {
        int shardTotal = resolveShardTotal(job.getShardTotal());
        int failedShards = 0;
        for (int shardIndex = 0; shardIndex < shardTotal; shardIndex++) {
            if (!executeShard(job, shardIndex, shardTotal, runId, triggerType)) {
                failedShards++;
            }
        }
        if (failedShards > 0) {
            log.warn("任务分片执行完成，存在失败分片: jobId={}, 失败分片数={}/{}",
                    job.getId(), failedShards, shardTotal);
            return;
        }
        triggerDownstream(job, runId, depth);
    }

    /**
     * 触发下游依赖任务：仅对上游全部成功的下游任务以 {@code CHAIN} 类型异步提交执行。
     *
     * <p>依赖链深度超过 {@value #MAX_CHAIN_DEPTH} 时告警并停止下传。</p>
     *
     * @param job 刚执行成功的上游任务
     * @param runId 执行批次ID（下游沿用）
     * @param depth 当前依赖链深度
     */
    void triggerDownstream(JobInfo job, String runId, int depth) {
        if (depth >= MAX_CHAIN_DEPTH) {
            log.warn("任务依赖链深度超过上限，停止下传: jobId={}, runId={}, depth={}",
                    job.getId(), runId, depth);
            return;
        }
        List<Long> downstreamIds = listDownstreamJobIds(job.getId());
        if (downstreamIds.isEmpty()) {
            return;
        }
        List<JobInfo> downstreamJobs = jobInfoMapper.selectList(new LambdaQueryWrapper<JobInfo>()
                .in(JobInfo::getId, downstreamIds)
                .eq(JobInfo::getStatus, STATUS_RUNNING));
        downstreamJobs.forEach(downstream -> submitDownstream(downstream, runId, depth));
    }

    /**
     * 提交单个下游任务：门禁通过则以同一 {@code runId}、{@code CHAIN} 类型提交调度线程池
     *
     * @param downstream 下游任务
     * @param runId 执行批次ID
     * @param depth 当前依赖链深度
     */
    private void submitDownstream(JobInfo downstream, String runId, int depth) {
        if (!isUpstreamSatisfied(downstream.getId(), runId)) {
            log.debug("下游任务的上游未全部在本轮成功，跳过链式触发: downstreamJobId={}, runId={}",
                    downstream.getId(), runId);
            return;
        }
        taskScheduler.execute(() -> executeWithRun(downstream, runId, TRIGGER_CHAIN, depth + 1));
        log.info("上游任务成功，已提交下游链式触发: downstreamJobId={}, runId={}, depth={}",
                downstream.getId(), runId, depth + 1);
    }

    /**
     * 上游门禁：下游任务的全部上游在同一 {@code runId} 下都必须存在成功执行记录
     *
     * @param downstreamJobId 下游任务ID
     * @param runId 执行批次ID
     * @return 全部上游均成功返回 {@code true}；任一上游无记录或未成功返回 {@code false}
     */
    boolean isUpstreamSatisfied(Long downstreamJobId, String runId) {
        List<JobDependency> dependencies = jobDependencyMapper.selectList(new LambdaQueryWrapper<JobDependency>()
                .eq(JobDependency::getJobId, downstreamJobId));
        for (JobDependency dependency : dependencies) {
            Long successCount = jobLogMapper.selectCount(new LambdaQueryWrapper<JobLog>()
                    .eq(JobLog::getJobId, dependency.getUpstreamJobId())
                    .eq(JobLog::getRunId, runId)
                    .eq(JobLog::getStatus, LOG_STATUS_SUCCESS));
            if (successCount == null || successCount <= 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 查询指定任务的下游任务ID列表
     *
     * @param jobId 上游任务ID
     * @return 下游任务ID列表（去重）
     */
    private List<Long> listDownstreamJobIds(Long jobId) {
        return jobDependencyMapper.selectList(new LambdaQueryWrapper<JobDependency>()
                        .eq(JobDependency::getUpstreamJobId, jobId)).stream()
                .map(JobDependency::getJobId)
                .distinct()
                .toList();
    }

    /**
     * 生成执行批次ID：UUID 去横线
     *
     * @return 32 位执行批次ID
     */
    private static String newRunId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 执行单个分片：失败时按上限与间隔串行重试，每次尝试回写 {@code retry_count}
     *
     * @param job        任务定义
     * @param shardIndex 当前分片序号（从 0 开始）
     * @param shardTotal 分片总数
     * @param runId      执行批次ID
     * @param triggerType 触发类型
     * @return 最终成功返回 {@code true}；重试耗尽仍失败返回 {@code false}
     */
    private boolean executeShard(JobInfo job, int shardIndex, int shardTotal, String runId, String triggerType) {
        JobLog jobLog = insertRunningLog(job, shardIndex, runId, triggerType);
        int maxRetry = resolveMaxRetry(job.getMaxRetry());
        long intervalMillis = resolveRetryIntervalSeconds(job.getRetryIntervalSeconds()) * MILLIS_PER_SECOND;
        for (int retryCount = 0; ; retryCount++) {
            try {
                String result = invokeHandler(job, shardIndex, shardTotal, retryCount);
                markSuccess(jobLog, result, retryCount);
                return true;
            } catch (Exception e) {
                if (retryCount >= maxRetry) {
                    markFailure(job, jobLog, e, retryCount);
                    log.warn("任务执行失败且重试已耗尽: jobId={}, jobName={}, shardIndex={}/{}, retryCount={}",
                            job.getId(), job.getJobName(), shardIndex, shardTotal, retryCount, e);
                    return false;
                }
                jobLog.setRetryCount(retryCount + 1);
                jobLogMapper.updateById(jobLog);
                log.warn("任务执行失败，{} 秒后进行第 {} 次重试: jobId={}, shardIndex={}/{}",
                        intervalMillis / MILLIS_PER_SECOND, retryCount + 1, job.getId(), shardIndex, shardTotal, e);
                if (!awaitRetry(intervalMillis)) {
                    markFailure(job, jobLog, e, retryCount + 1);
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
     * @param runId      执行批次ID
     * @param triggerType 触发类型
     * @return 已落库的执行日志实体（主键已回填）
     */
    private JobLog insertRunningLog(JobInfo job, int shardIndex, String runId, String triggerType) {
        JobLog jobLog = new JobLog();
        jobLog.setJobId(job.getId());
        jobLog.setJobName(job.getJobName());
        jobLog.setHandlerBean(job.getHandlerBean());
        jobLog.setRunId(runId);
        jobLog.setTriggerType(triggerType);
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
     * 回写失败终态（重试耗尽或被中断），随后发送失败告警。
     *
     * <p>告警组件异常一律吞掉，绝不影响调度主流程。</p>
     *
     * @param job        任务定义
     * @param jobLog     执行日志实体
     * @param e          失败异常
     * @param retryCount 本次执行已重试次数
     */
    private void markFailure(JobInfo job, JobLog jobLog, Exception e, int retryCount) {
        jobLog.setEndTime(LocalDateTime.now());
        jobLog.setStatus(LOG_STATUS_FAILURE);
        jobLog.setRetryCount(retryCount);
        jobLog.setErrorMsg(truncate(e.getClass().getSimpleName()
                + (e.getMessage() == null ? "" : ": " + e.getMessage())));
        jobLogMapper.updateById(jobLog);
        notifyFailure(job, jobLog);
    }

    /**
     * 发送失败告警：告警组件异常只记录，绝不外抛
     *
     * @param job 任务定义
     * @param jobLog 已回写失败终态的执行日志
     */
    private void notifyFailure(JobInfo job, JobLog jobLog) {
        try {
            jobAlertNotifier.notifyFailure(job, jobLog);
        } catch (Exception e) {
            log.warn("任务失败告警发送异常，已忽略: jobId={}, logId={}", job.getId(), jobLog.getId(), e);
        }
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
