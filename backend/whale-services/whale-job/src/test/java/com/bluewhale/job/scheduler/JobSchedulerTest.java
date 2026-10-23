package com.bluewhale.job.scheduler;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.job.alert.JobAlertNotifier;
import com.bluewhale.job.entity.JobInfo;
import com.bluewhale.job.entity.JobLog;
import com.bluewhale.job.mapper.JobDependencyMapper;
import com.bluewhale.job.mapper.JobInfoMapper;
import com.bluewhale.job.mapper.JobLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JobScheduler} 执行防重、misfire 补偿与执行日志落库单元测试。
 *
 * @author BlueWhale
 */
class JobSchedulerTest {

    private static final int NOT_FOUND_CODE = 404;
    private static final int LOG_STATUS_RUNNING = 0;
    private static final int LOG_STATUS_SUCCESS = 1;
    private static final int LOG_STATUS_FAILURE = -1;

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

    @AfterEach
    void tearDown() {
        scheduler.destroy();
    }

    private static JobInfo job(Long id, String misfirePolicy) {
        JobInfo job = new JobInfo();
        job.setId(id);
        job.setJobName("测试任务");
        job.setHandlerBean("demoJobHandler");
        job.setCronExpression("0 0/5 * * * ?");
        job.setMisfirePolicy(misfirePolicy);
        job.setStatus(1);
        return job;
    }

    @Test
    @DisplayName("doExecute：执行成功回写 end_time、status=1 与 result")
    void shouldPersistSuccessLog() {
        when(jobHandlerRegistry.resolve("demoJobHandler")).thenReturn(param -> "done");
        AtomicReference<JobLog> insertedLog = new AtomicReference<>();
        AtomicInteger insertedStatus = new AtomicInteger();
        doAnswer(invocation -> {
            JobLog log = invocation.getArgument(0);
            insertedLog.set(log);
            insertedStatus.set(log.getStatus());
            return 1;
        }).when(jobLogMapper).insert(any(JobLog.class));

        scheduler.doExecute(job(1L, "SMART"));

        verify(jobLogMapper).insert(any(JobLog.class));
        verify(jobLogMapper).updateById(any(JobLog.class));
        assertEquals(LOG_STATUS_RUNNING, insertedStatus.get());
        assertEquals(LOG_STATUS_SUCCESS, insertedLog.get().getStatus());
        assertEquals("done", insertedLog.get().getResult());
        assertNotNull(insertedLog.get().getEndTime());
    }

    @Test
    @DisplayName("doExecute：执行器抛异常回写 status=-1 与 error_msg")
    void shouldPersistFailureLog() {
        when(jobHandlerRegistry.resolve("demoJobHandler")).thenReturn(param -> {
            throw new IllegalStateException("boom");
        });

        scheduler.doExecute(job(1L, "SMART"));

        ArgumentCaptor<JobLog> updateCaptor = ArgumentCaptor.forClass(JobLog.class);
        verify(jobLogMapper).updateById(updateCaptor.capture());
        assertEquals(LOG_STATUS_FAILURE, updateCaptor.getValue().getStatus());
        assertTrue(updateCaptor.getValue().getErrorMsg().contains("boom"));
    }

    @Test
    @DisplayName("doExecute：执行器解析失败也按失败落库且不外抛")
    void shouldPersistFailureWhenHandlerMissing() {
        when(jobHandlerRegistry.resolve(anyString())).thenThrow(new BizException("执行器不存在"));

        scheduler.doExecute(job(1L, "SMART"));

        ArgumentCaptor<JobLog> updateCaptor = ArgumentCaptor.forClass(JobLog.class);
        verify(jobLogMapper).updateById(updateCaptor.capture());
        assertEquals(LOG_STATUS_FAILURE, updateCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("execute：正常执行后释放实例内互斥")
    void shouldReleaseGuardAfterExecution() {
        when(jobHandlerRegistry.resolve("demoJobHandler")).thenReturn(param -> "ok");

        scheduler.execute(job(1L, "SMART"));

        assertFalse(executionGuard.isRunning(1L));
        verify(jobLogMapper).updateById(any(JobLog.class));
    }

    @Test
    @DisplayName("execute：触发重叠且策略为 DO_NOTHING 时跳过且不补跑")
    void shouldSkipWhenMisfireDoNothing() {
        Long jobId = 1L;
        executionGuard.tryEnter(jobId);

        scheduler.execute(job(jobId, "DO_NOTHING"));

        assertFalse(executionGuard.consumePendingOnce(jobId));
        verify(jobLogMapper, never()).insert(any(JobLog.class));
    }

    @Test
    @DisplayName("execute：触发重叠且策略为 SMART 时登记待补跑一次")
    void shouldMarkPendingWhenMisfireSmart() {
        Long jobId = 1L;
        executionGuard.tryEnter(jobId);

        scheduler.execute(job(jobId, "SMART"));

        assertTrue(executionGuard.consumePendingOnce(jobId));
    }

    @Test
    @DisplayName("execute：执行期间再次触发，结束后补跑一次")
    void shouldCompensateOnceAfterOverlap() {
        Long jobId = 1L;
        JobInfo job = job(jobId, "SMART");
        AtomicInteger runs = new AtomicInteger();
        when(jobHandlerRegistry.resolve("demoJobHandler")).thenReturn(param -> {
            int run = runs.incrementAndGet();
            if (run == 1) {
                scheduler.execute(job);
            }
            return "run-" + run;
        });

        scheduler.execute(job);

        assertEquals(2, runs.get());
        assertFalse(executionGuard.isRunning(jobId));
    }

    @Test
    @DisplayName("runOnce：任务不存在抛 NOT_FOUND")
    void shouldThrowWhenRunOnceMissing() {
        when(jobInfoMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> scheduler.runOnce(99L));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
        verify(jobLogMapper, never()).insert(any(JobLog.class));
    }

    @Test
    @DisplayName("runOnce：异步触发任务并落执行日志")
    void shouldRunOnceAsynchronously() {
        scheduler.init();
        when(jobInfoMapper.selectById(1L)).thenReturn(job(1L, "SMART"));
        when(jobHandlerRegistry.resolve("demoJobHandler")).thenReturn(param -> "ok");

        scheduler.runOnce(1L);

        verify(jobLogMapper, timeout(5000)).updateById(any(JobLog.class));
    }

    @Test
    @DisplayName("重试：未配置 maxRetry 时失败即终态，仅尝试一次")
    void shouldFailImmediatelyWhenRetryNotConfigured() {
        AtomicInteger attempts = new AtomicInteger();
        when(jobHandlerRegistry.resolve("demoJobHandler")).thenReturn(param -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("boom");
        });

        scheduler.doExecute(job(1L, "SMART"));

        assertEquals(1, attempts.get());
        ArgumentCaptor<JobLog> insertCaptor = ArgumentCaptor.forClass(JobLog.class);
        ArgumentCaptor<JobLog> updateCaptor = ArgumentCaptor.forClass(JobLog.class);
        verify(jobLogMapper).insert(insertCaptor.capture());
        verify(jobLogMapper).updateById(updateCaptor.capture());
        assertEquals(LOG_STATUS_FAILURE, updateCaptor.getValue().getStatus());
        assertEquals(0, updateCaptor.getValue().getRetryCount().intValue());
        assertEquals(0, insertCaptor.getValue().getShardIndex().intValue());
    }

    @Test
    @DisplayName("重试：maxRetry=2 时最多尝试 3 次，终态 retryCount=2")
    void shouldRetryUntilMaxRetry() {
        AtomicInteger attempts = new AtomicInteger();
        when(jobHandlerRegistry.resolve("demoJobHandler")).thenReturn(param -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("boom");
        });
        JobInfo job = job(1L, "SMART");
        job.setMaxRetry(2);
        job.setRetryIntervalSeconds(0);

        scheduler.doExecute(job);

        assertEquals(3, attempts.get());
        ArgumentCaptor<JobLog> updateCaptor = ArgumentCaptor.forClass(JobLog.class);
        verify(jobLogMapper, times(3)).updateById(updateCaptor.capture());
        assertEquals(LOG_STATUS_FAILURE, updateCaptor.getValue().getStatus());
        assertEquals(2, updateCaptor.getValue().getRetryCount().intValue());
    }

    @Test
    @DisplayName("重试：失败一次后成功则不再重试")
    void shouldStopRetryingAfterSuccess() {
        AtomicInteger attempts = new AtomicInteger();
        when(jobHandlerRegistry.resolve("demoJobHandler")).thenReturn(param -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("boom");
            }
            return "ok";
        });
        JobInfo job = job(1L, "SMART");
        job.setMaxRetry(2);
        job.setRetryIntervalSeconds(0);

        scheduler.doExecute(job);

        assertEquals(2, attempts.get());
        ArgumentCaptor<JobLog> updateCaptor = ArgumentCaptor.forClass(JobLog.class);
        verify(jobLogMapper, times(2)).updateById(updateCaptor.capture());
        JobLog last = updateCaptor.getValue();
        assertEquals(LOG_STATUS_SUCCESS, last.getStatus());
        assertEquals(1, last.getRetryCount().intValue());
        assertEquals("ok", last.getResult());
    }

    @Test
    @DisplayName("分片：shardTotal=3 时执行器依次收到 0/1/2 三个分片")
    void shouldPassShardIndexToHandler() {
        List<JobExecutionContext> contexts = new ArrayList<>();
        when(jobHandlerRegistry.resolve("demoJobHandler")).thenReturn(new JobHandler() {
            @Override
            public String execute(String param) {
                return "unused";
            }

            @Override
            public String executeWithContext(JobExecutionContext context) {
                contexts.add(context);
                return "shard-" + context.getShardIndex();
            }
        });
        JobInfo job = job(1L, "SMART");
        job.setShardTotal(3);

        scheduler.doExecute(job);

        assertEquals(3, contexts.size());
        for (int shardIndex = 0; shardIndex < contexts.size(); shardIndex++) {
            assertEquals(shardIndex, contexts.get(shardIndex).getShardIndex());
            assertEquals(3, contexts.get(shardIndex).getShardTotal());
        }
        ArgumentCaptor<JobLog> insertCaptor = ArgumentCaptor.forClass(JobLog.class);
        verify(jobLogMapper, times(3)).insert(insertCaptor.capture());
        List<Integer> shardIndexes = insertCaptor.getAllValues().stream()
                .map(JobLog::getShardIndex).toList();
        assertEquals(List.of(0, 1, 2), shardIndexes);
    }

    @Test
    @DisplayName("分片：shardTotal=1 时旧执行器（仅实现 execute(String)）行为不变")
    void shouldKeepLegacyBehaviorForSingleShard() {
        when(jobHandlerRegistry.resolve("demoJobHandler")).thenReturn(param -> "legacy:" + param);
        JobInfo job = job(1L, "SMART");
        job.setHandlerParam("p");

        scheduler.doExecute(job);

        ArgumentCaptor<JobLog> insertCaptor = ArgumentCaptor.forClass(JobLog.class);
        ArgumentCaptor<JobLog> updateCaptor = ArgumentCaptor.forClass(JobLog.class);
        verify(jobLogMapper).insert(insertCaptor.capture());
        verify(jobLogMapper).updateById(updateCaptor.capture());
        assertEquals(0, insertCaptor.getValue().getShardIndex().intValue());
        assertEquals(0, insertCaptor.getValue().getRetryCount().intValue());
        assertEquals(LOG_STATUS_SUCCESS, updateCaptor.getValue().getStatus());
        assertEquals("legacy:p", updateCaptor.getValue().getResult());
    }

    @Test
    @DisplayName("loadRunningJobs：合法 cron 任务注册、非法 cron 任务跳过且不抛异常")
    void shouldRegisterOnlyValidCronJobs() {
        scheduler.init();
        JobInfo invalid = job(2L, "SMART");
        invalid.setCronExpression("not-a-cron");
        when(jobInfoMapper.selectList(any())).thenReturn(List.of(job(1L, "SMART"), invalid));

        scheduler.loadRunningJobs();

        scheduler.cancel(1L);
        scheduler.cancel(2L);
    }
}
