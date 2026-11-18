package com.bluewhale.job.alert;

import com.bluewhale.job.config.JobProperties;
import com.bluewhale.job.po.JobAlertPo;
import com.bluewhale.job.po.JobInfoPo;
import com.bluewhale.job.po.JobLogPo;
import com.bluewhale.job.mapper.JobAlertMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WebhookJobAlertNotifier} 失败告警落库、webhook 外发与冷却去重单元测试。
 *
 * @author BlueWhale
 */
class WebhookJobAlertNotifierTest {

    private static final String WEBHOOK_URL = "http://127.0.0.1:1/job-alert";

    private static final int STATUS_SUCCESS = 1;
    private static final int STATUS_FAILURE = -1;

    private final JobProperties jobProperties = new JobProperties();

    private final JobAlertMapper jobAlertMapper = mock(JobAlertMapper.class);

    private final ClientHttpRequestFactory requestFactory = mock(ClientHttpRequestFactory.class);

    private final RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();

    private final WebhookJobAlertNotifier notifier =
            new WebhookJobAlertNotifier(jobProperties, jobAlertMapper, restClient, Runnable::run);

    @AfterEach
    void tearDown() {
        notifier.destroy();
    }

    private static JobInfoPo job() {
        JobInfoPo job = new JobInfoPo();
        job.setId(1L);
        job.setJobName("任务A");
        return job;
    }

    private static JobLogPo log() {
        JobLogPo jobLog = new JobLogPo();
        jobLog.setId(100L);
        jobLog.setJobId(1L);
        jobLog.setRunId("run-abc");
        jobLog.setRetryCount(2);
        jobLog.setErrorMsg("IllegalStateException: boom");
        return jobLog;
    }

    @Test
    @DisplayName("告警：每次失败都落库，webhook 为空时仅走 LOG 通道")
    void shouldPersistAlertWithoutWebhook() throws IOException {
        jobProperties.getAlert().setWebhookUrl("");

        notifier.notifyFailure(job(), log());

        ArgumentCaptor<JobAlertPo> captor = ArgumentCaptor.forClass(JobAlertPo.class);
        verify(jobAlertMapper).insert(captor.capture());
        JobAlertPo alert = captor.getValue();
        assertEquals(1L, alert.getJobId().longValue());
        assertEquals("任务A", alert.getJobName());
        assertEquals(100L, alert.getLogId().longValue());
        assertEquals("run-abc", alert.getRunId());
        assertEquals("ERROR", alert.getLevel());
        assertEquals("LOG", alert.getChannel());
        assertEquals(STATUS_SUCCESS, alert.getStatus().intValue());
        assertEquals("IllegalStateException: boom", alert.getErrorMsg());
        verify(requestFactory, never()).createRequest(any(), any());
    }

    @Test
    @DisplayName("告警：错误信息按 max-error-length 截断后落库")
    void shouldTruncateErrorMessage() {
        jobProperties.getAlert().setWebhookUrl("");
        jobProperties.getAlert().setMaxErrorLength(10);
        JobLogPo jobLog = log();
        jobLog.setErrorMsg("0123456789abcdef");

        notifier.notifyFailure(job(), jobLog);

        ArgumentCaptor<JobAlertPo> captor = ArgumentCaptor.forClass(JobAlertPo.class);
        verify(jobAlertMapper).insert(captor.capture());
        assertEquals("0123456789", captor.getValue().getErrorMsg());
    }

    @Test
    @DisplayName("告警：webhook 异常不影响主流程并回写失败状态")
    void shouldSwallowWebhookFailure() throws IOException {
        jobProperties.getAlert().setWebhookUrl(WEBHOOK_URL);
        when(requestFactory.createRequest(any(URI.class), any(HttpMethod.class)))
                .thenThrow(new IOException("connect refused"));

        assertDoesNotThrow(() -> notifier.notifyFailure(job(), log()));

        ArgumentCaptor<JobAlertPo> insertCaptor = ArgumentCaptor.forClass(JobAlertPo.class);
        verify(jobAlertMapper).insert(insertCaptor.capture());
        assertEquals("WEBHOOK", insertCaptor.getValue().getChannel());
        ArgumentCaptor<JobAlertPo> updateCaptor = ArgumentCaptor.forClass(JobAlertPo.class);
        verify(jobAlertMapper).updateById(updateCaptor.capture());
        assertEquals(STATUS_FAILURE, updateCaptor.getValue().getStatus().intValue());
        assertNotNull(updateCaptor.getValue().getMessage());
    }

    @Test
    @DisplayName("告警：同一任务冷却期内只外发一次，但每次失败仍落库")
    void shouldDeduplicateWebhookWithinCooldown() throws IOException {
        jobProperties.getAlert().setWebhookUrl(WEBHOOK_URL);
        jobProperties.getAlert().setCooldownSeconds(300);
        when(requestFactory.createRequest(any(URI.class), any(HttpMethod.class)))
                .thenThrow(new IOException("connect refused"));

        notifier.notifyFailure(job(), log());
        notifier.notifyFailure(job(), log());

        verify(requestFactory, times(1)).createRequest(any(URI.class), any(HttpMethod.class));
        ArgumentCaptor<JobAlertPo> captor = ArgumentCaptor.forClass(JobAlertPo.class);
        verify(jobAlertMapper, times(2)).insert(captor.capture());
        List<JobAlertPo> alerts = captor.getAllValues();
        assertEquals("WEBHOOK", alerts.get(0).getChannel());
        assertEquals("LOG", alerts.get(1).getChannel());
        verify(jobAlertMapper, times(1)).updateById(any(JobAlertPo.class));
    }

    @Test
    @DisplayName("冷却：Redis 可用时以 SETNX 决定外发资格，跨实例共享冷却窗口")
    void shouldUseRedisForCooldown() throws IOException {
        jobProperties.getAlert().setWebhookUrl(WEBHOOK_URL);
        jobProperties.getAlert().setCooldownSeconds(300);
        when(requestFactory.createRequest(any(URI.class), any(HttpMethod.class)))
                .thenThrow(new IOException("connect refused"));
        RedissonClient client = mock(RedissonClient.class);
        @SuppressWarnings("unchecked")
        RBucket<Object> bucket = mock(RBucket.class);
        when(client.getBucket("job:alert:webhook:cooldown:0:1")).thenReturn(bucket);
        when(bucket.setIfAbsent(any(), any(Duration.class))).thenReturn(true, false);
        WebhookJobAlertNotifier redisNotifier = redisNotifier(client);
        try {
            redisNotifier.notifyFailure(job(), log());
            redisNotifier.notifyFailure(job(), log());

            verify(bucket, times(2)).setIfAbsent(any(), any(Duration.class));
            verify(requestFactory, times(1)).createRequest(any(URI.class), any(HttpMethod.class));
        } finally {
            redisNotifier.destroy();
        }
    }

    @Test
    @DisplayName("冷却：Redis 异常时降级为实例内冷却，不中断告警")
    void shouldFallbackToLocalCooldownWhenRedisFails() throws IOException {
        jobProperties.getAlert().setWebhookUrl(WEBHOOK_URL);
        jobProperties.getAlert().setCooldownSeconds(300);
        when(requestFactory.createRequest(any(URI.class), any(HttpMethod.class)))
                .thenThrow(new IOException("connect refused"));
        RedissonClient client = mock(RedissonClient.class);
        when(client.getBucket(any(String.class))).thenThrow(new IllegalStateException("redis down"));
        WebhookJobAlertNotifier redisNotifier = redisNotifier(client);
        try {
            redisNotifier.notifyFailure(job(), log());
            redisNotifier.notifyFailure(job(), log());

            verify(client, times(2)).getBucket("job:alert:webhook:cooldown:0:1");
            verify(requestFactory, times(1)).createRequest(any(URI.class), any(HttpMethod.class));
        } finally {
            redisNotifier.destroy();
        }
    }

    @Test
    @DisplayName("告警：超时使用独立摘要，自动暂停使用 WARN 级别且不关联执行日志")
    void shouldDistinguishTimeoutAndAutoPaused() {
        jobProperties.getAlert().setWebhookUrl("");

        notifier.notifyTimeout(job(), log());
        notifier.notifyAutoPaused(job(), 3);

        ArgumentCaptor<JobAlertPo> captor = ArgumentCaptor.forClass(JobAlertPo.class);
        verify(jobAlertMapper, times(2)).insert(captor.capture());
        List<JobAlertPo> alerts = captor.getAllValues();
        assertEquals("任务执行超时：任务A", alerts.get(0).getMessage());
        assertEquals("ERROR", alerts.get(0).getLevel());
        assertEquals("任务连续失败已自动暂停：任务A", alerts.get(1).getMessage());
        assertEquals("WARN", alerts.get(1).getLevel());
        assertNull(alerts.get(1).getLogId());
        assertNull(alerts.get(1).getRunId());
    }

    /**
     * 构造注入了 Redis 客户端的告警通知器
     *
     * @param client Redis 客户端
     * @return 通知器实例
     */
    @SuppressWarnings("unchecked")
    private WebhookJobAlertNotifier redisNotifier(RedissonClient client) {
        ObjectProvider<RedissonClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        return new WebhookJobAlertNotifier(jobProperties, jobAlertMapper, restClient, Runnable::run, provider);
    }
}
