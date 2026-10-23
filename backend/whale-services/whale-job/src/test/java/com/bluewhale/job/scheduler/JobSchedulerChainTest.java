package com.bluewhale.job.scheduler;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.job.alert.JobAlertNotifier;
import com.bluewhale.job.entity.JobDependency;
import com.bluewhale.job.entity.JobInfo;
import com.bluewhale.job.entity.JobLog;
import com.bluewhale.job.mapper.JobDependencyMapper;
import com.bluewhale.job.mapper.JobInfoMapper;
import com.bluewhale.job.mapper.JobLogMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link JobScheduler} 任务 DAG 依赖编排单元测试：上游门禁、失败不下传、链深度上限与 cron 注册跳过。
 *
 * @author BlueWhale
 */
class JobSchedulerChainTest {

    private static final String RUN_ID = "run-0123456789abcdef";

    private static final String TRIGGER_CRON = "CRON";

    private static final String TRIGGER_CHAIN = "CHAIN";

    private static final String TRIGGER_MANUAL = "MANUAL";

    private static final int MAX_CHAIN_DEPTH = 32;

    private final JobInfoMapper jobInfoMapper = mock(JobInfoMapper.class);

    private final JobLogMapper jobLogMapper = mock(JobLogMapper.class);

    private final JobDependencyMapper jobDependencyMapper = mock(JobDependencyMapper.class);

    private final JobHandlerRegistry jobHandlerRegistry = mock(JobHandlerRegistry.class);

    private final JobExecutionGuard executionGuard = new JobExecutionGuard();

    private final JobAlertNotifier jobAlertNotifier = mock(JobAlertNotifier.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<RedissonClient> redissonClientProvider =
            (ObjectProvider<RedissonClient>) mock(ObjectProvider.class);

    private final JobScheduler scheduler = new JobScheduler(jobInfoMapper, jobLogMapper, jobDependencyMapper,
            jobHandlerRegistry, executionGuard, redissonClientProvider, jobAlertNotifier);

    /**
     * 注册实体元信息缓存：Lambda 条件构造器会立即按实体解析列名，纯单测无 MyBatis 环境需手动初始化 TableInfo。
     */
    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, JobInfo.class);
        TableInfoHelper.initTableInfo(assistant, JobDependency.class);
        TableInfoHelper.initTableInfo(assistant, JobLog.class);
    }

    @BeforeEach
    void setUp() {
        scheduler.init();
    }

    @AfterEach
    void tearDown() {
        scheduler.destroy();
    }

    private static JobInfo job(Long id) {
        JobInfo job = new JobInfo();
        job.setId(id);
        job.setJobName("任务" + id);
        job.setHandlerBean("demoJobHandler");
        job.setCronExpression("0 0/5 * * * ?");
        job.setMisfirePolicy("SMART");
        job.setStatus(1);
        return job;
    }

    private static JobDependency dependency(Long jobId, Long upstreamJobId) {
        JobDependency dependency = new JobDependency();
        dependency.setJobId(jobId);
        dependency.setUpstreamJobId(upstreamJobId);
        return dependency;
    }

    /**
     * 读取调度器内部已注册调度映射（用于断言有上游依赖时未注册 cron）
     *
     * @param scheduler 调度器
     * @return jobId 到 ScheduledFuture 的映射
     * @throws Exception 反射失败时抛出
     */
    @SuppressWarnings("unchecked")
    private static Map<Long, ?> scheduledFutures(JobScheduler scheduler) throws Exception {
        Field field = JobScheduler.class.getDeclaredField("scheduledFutures");
        field.setAccessible(true);
        return (Map<Long, ?>) field.get(scheduler);
    }

    @Test
    @DisplayName("门禁：全部上游在同一 runId 下成功时放行")
    void shouldPassGateWhenAllUpstreamsSucceeded() {
        when(jobDependencyMapper.selectList(any()))
                .thenReturn(List.of(dependency(2L, 1L), dependency(2L, 3L)));
        when(jobLogMapper.selectCount(any())).thenReturn(1L);

        assertTrue(scheduler.isUpstreamSatisfied(2L, RUN_ID));
    }

    @Test
    @DisplayName("门禁：任一上游在本轮无成功记录时拦截")
    void shouldBlockGateWhenAnyUpstreamMissing() {
        when(jobDependencyMapper.selectList(any()))
                .thenReturn(List.of(dependency(2L, 1L), dependency(2L, 3L)));
        when(jobLogMapper.selectCount(any())).thenReturn(1L, 0L);

        assertFalse(scheduler.isUpstreamSatisfied(2L, RUN_ID));
    }

    @Test
    @DisplayName("链式触发：上游成功后以同一 runId、CHAIN 类型触发下游")
    void shouldTriggerDownstreamWithSameRunId() {
        when(jobHandlerRegistry.resolve("demoJobHandler")).thenReturn(param -> "ok");
        when(jobDependencyMapper.selectList(any()))
                .thenReturn(List.of(dependency(2L, 1L)), List.of());
        when(jobInfoMapper.selectList(any())).thenReturn(List.of(job(2L)));
        when(jobLogMapper.selectCount(any())).thenReturn(1L);

        scheduler.doExecute(job(1L), RUN_ID, TRIGGER_CRON, 0);

        ArgumentCaptor<JobLog> insertCaptor = ArgumentCaptor.forClass(JobLog.class);
        verify(jobLogMapper, timeout(3000).times(2)).insert(insertCaptor.capture());
        verify(jobLogMapper, timeout(3000).times(2)).updateById(any(JobLog.class));

        JobLog upstreamLog = insertCaptor.getAllValues().get(0);
        JobLog downstreamLog = insertCaptor.getAllValues().get(1);
        assertEquals(1L, upstreamLog.getJobId().longValue());
        assertEquals(TRIGGER_CRON, upstreamLog.getTriggerType());
        assertEquals(2L, downstreamLog.getJobId().longValue());
        assertEquals(RUN_ID, downstreamLog.getRunId());
        assertEquals(TRIGGER_CHAIN, downstreamLog.getTriggerType());
        assertEquals(1, downstreamLog.getStatus().intValue());
    }

    @Test
    @DisplayName("链式触发：门禁不通过时下游不执行")
    void shouldNotTriggerDownstreamWhenGateBlocked() {
        when(jobHandlerRegistry.resolve("demoJobHandler")).thenReturn(param -> "ok");
        when(jobDependencyMapper.selectList(any()))
                .thenReturn(List.of(dependency(2L, 1L)), List.of(dependency(2L, 1L)));
        when(jobInfoMapper.selectList(any())).thenReturn(List.of(job(2L)));
        when(jobLogMapper.selectCount(any())).thenReturn(0L);

        scheduler.doExecute(job(1L), RUN_ID, TRIGGER_CRON, 0);

        verify(jobLogMapper, after(400).times(1)).insert(any(JobLog.class));
    }

    @Test
    @DisplayName("链式触发：任务失败时不触发任何下游")
    void shouldNotTriggerDownstreamWhenJobFails() {
        when(jobHandlerRegistry.resolve("demoJobHandler")).thenReturn(param -> {
            throw new IllegalStateException("boom");
        });

        scheduler.doExecute(job(1L), RUN_ID, TRIGGER_CRON, 0);

        verify(jobLogMapper).insert(any(JobLog.class));
        verify(jobDependencyMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("链式触发：深度达到上限时告警并停止下传")
    void shouldStopWhenChainDepthExceeded() {
        scheduler.triggerDownstream(job(1L), RUN_ID, MAX_CHAIN_DEPTH);

        verifyNoInteractions(jobDependencyMapper);
    }

    @Test
    @DisplayName("注册：存在上游依赖的任务跳过 cron 注册")
    void shouldSkipCronRegistrationWhenUpstreamExists() throws Exception {
        when(jobDependencyMapper.selectCount(any())).thenReturn(1L);
        when(jobInfoMapper.selectList(any())).thenReturn(List.of(job(1L)));

        scheduler.loadRunningJobs();

        assertTrue(scheduledFutures(scheduler).isEmpty());
    }

    @Test
    @DisplayName("注册：无上游依赖的任务仍按 cron 注册")
    void shouldRegisterCronWhenNoUpstream() throws Exception {
        when(jobDependencyMapper.selectCount(any())).thenReturn(0L);
        when(jobInfoMapper.selectList(any())).thenReturn(List.of(job(1L)));

        scheduler.loadRunningJobs();

        assertEquals(1, scheduledFutures(scheduler).size());
    }

    @Test
    @DisplayName("手动触发：忽略上游门禁直接执行，触发类型为 MANUAL")
    void shouldRunManuallyRegardlessOfUpstreamGate() {
        when(jobInfoMapper.selectById(2L)).thenReturn(job(2L));
        when(jobHandlerRegistry.resolve("demoJobHandler")).thenReturn(param -> "ok");

        scheduler.runOnce(2L);

        ArgumentCaptor<JobLog> captor = ArgumentCaptor.forClass(JobLog.class);
        verify(jobLogMapper, timeout(3000)).insert(captor.capture());
        assertEquals(TRIGGER_MANUAL, captor.getValue().getTriggerType());
        assertNotNull(captor.getValue().getRunId());
        assertFalse(captor.getValue().getRunId().isEmpty());
    }
}
