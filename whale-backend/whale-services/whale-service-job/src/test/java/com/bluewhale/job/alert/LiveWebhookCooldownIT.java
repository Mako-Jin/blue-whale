package com.bluewhale.job.alert;

import com.bluewhale.job.config.JobProperties;
import com.bluewhale.job.mapper.JobAlertMapper;
import com.bluewhale.job.po.JobAlertPo;
import com.bluewhale.job.po.JobInfoPo;
import com.bluewhale.job.po.JobLogPo;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.redisson.Redisson;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Webhook 告警冷却的实时集成测试（真实 Redisson 直连 Redis @ 127.0.0.1:6379）。
 *
 * <p><b>默认跳过</b>：仅在系统属性 {@code -Dwhale.live.it=true} 时执行。</p>
 * <p>执行命令（cwd {@code f:\blue-whale\whale-backend\whale-services\whale-service-job}）：</p>
 * <pre>
 * mvn -o test -Dwhale.live.it=true -Dtest=LiveWebhookCooldownIT -DfailIfNoTests=false
 * </pre>
 *
 * <p>验证目标：两个独立通知器实例（模拟两个服务实例）共享同一冷却窗口，
 * 冷却期内只有第一个实例外发 webhook，告警记录仍逐次落库。</p>
 *
 * @author BlueWhale
 */
@EnabledIfSystemProperty(named = "whale.live.it", matches = "true")
class LiveWebhookCooldownIT {

    private static final String REDIS_ADDRESS = "redis://127.0.0.1:6379";
    private static final String COOLDOWN_KEY = "job:alert:webhook:cooldown:2:999001";
    private static final long JOB_ID = 999001L;
    private static final long TENANT_ID = 2L;
    private static final int COOLDOWN_SECONDS = 300;

    private static RedissonClient redissonClient;

    private static HttpServer webhookServer;

    private static AtomicInteger webhookHits;

    private static String webhookUrl;

    @BeforeAll
    static void setUpClass() throws IOException {
        Config config = new Config();
        config.useSingleServer().setAddress(REDIS_ADDRESS);
        redissonClient = Redisson.create(config);
        redissonClient.getBucket(COOLDOWN_KEY).delete();

        webhookHits = new AtomicInteger();
        webhookServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        webhookServer.createContext("/hook", exchange -> {
            webhookHits.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"code\":0}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        webhookServer.start();
        webhookUrl = "http://127.0.0.1:" + webhookServer.getAddress().getPort() + "/hook";
    }

    @AfterAll
    static void tearDownClass() {
        if (redissonClient != null) {
            redissonClient.getBucket(COOLDOWN_KEY).delete();
            redissonClient.shutdown();
            redissonClient = null;
        }
        if (webhookServer != null) {
            webhookServer.stop(0);
            webhookServer = null;
        }
    }

    @Test
    @DisplayName("两个实例共享 Redis 冷却窗口：冷却期内仅首个实例外发，告警仍逐次落库")
    void shouldShareCooldownAcrossInstances() {
        JobAlertMapper alertMapper = mock(JobAlertMapper.class);
        WebhookJobAlertNotifier instanceA = notifier(alertMapper);
        WebhookJobAlertNotifier instanceB = notifier(alertMapper);

        // 实例 A 首次失败：获得外发资格
        instanceA.notifyFailure(job(), jobLog());
        assertEquals(1, webhookHits.get());

        // 实例 B 在冷却期内失败：只落库不外发
        instanceB.notifyFailure(job(), jobLog());
        assertEquals(1, webhookHits.get());
        verify(alertMapper, times(2)).insert(any(JobAlertPo.class));

        // 冷却键存在且带 TTL
        RBucket<Long> bucket = redissonClient.getBucket(COOLDOWN_KEY);
        assertTrue(bucket.isExists());
        assertTrue(bucket.remainTimeToLive() > 0);

        // 冷却窗口失效后，实例 B 可再次外发
        bucket.delete();
        instanceB.notifyFailure(job(), jobLog());
        assertEquals(2, webhookHits.get());
        verify(alertMapper, times(3)).insert(any(JobAlertPo.class));
    }

    /**
     * 构造注入了共享 Redis 的通知器实例
     *
     * @param alertMapper 告警 Mapper
     * @return 通知器
     */
    @SuppressWarnings("unchecked")
    private WebhookJobAlertNotifier notifier(JobAlertMapper alertMapper) {
        JobProperties properties = new JobProperties();
        properties.getAlert().setEnabled(true);
        properties.getAlert().setWebhookUrl(webhookUrl);
        properties.getAlert().setCooldownSeconds(COOLDOWN_SECONDS);
        ObjectProvider<RedissonClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redissonClient);
        return new WebhookJobAlertNotifier(properties, alertMapper, RestClient.builder().build(), Runnable::run, provider);
    }

    /**
     * 构造失败任务
     *
     * @return 任务实体
     */
    private JobInfoPo job() {
        JobInfoPo job = new JobInfoPo();
        job.setId(JOB_ID);
        job.setTenantId(TENANT_ID);
        job.setJobName("live-cooldown");
        return job;
    }

    /**
     * 构造失败执行日志
     *
     * @return 日志实体
     */
    private JobLogPo jobLog() {
        JobLogPo log = new JobLogPo();
        log.setId(1L);
        log.setRunId("live-run");
        log.setRetryCount(0);
        log.setErrorMsg("boom");
        return log;
    }
}
