package com.bluewhale.twin.service;

import com.bluewhale.twin.config.TwinProperties;
import com.bluewhale.twin.dto.BindingAlertVO;
import com.bluewhale.twin.support.TwinAlertWebhookSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 告警外发服务单测：开关/配置短路、载荷组包与冷却抑制。
 *
 * <p>测试注入同步执行器（{@code Runnable::run}），避免异步断言竞态。</p>
 *
 * @author BlueWhale
 */
class TwinAlertNotifyServiceTest {

    private static final Long SCENE_ID = 100L;

    private static final Long TENANT_ID = 1L;

    private static final String URL = "https://hook.example.com/twin/alert";

    private TwinProperties properties;

    private TwinAlertWebhookSender sender;

    @BeforeEach
    void setUp() {
        properties = new TwinProperties();
        sender = mock(TwinAlertWebhookSender.class);
    }

    @Test
    void doesNothingWhenDisabled() {
        properties.getAlertNotify().setWebhookUrls(List.of(URL));

        service().notify(SCENE_ID, TENANT_ID, alert("critical"));

        verify(sender, never()).send(anyString(), anyMap());
    }

    @Test
    void doesNothingWhenNoUrlConfigured() {
        properties.getAlertNotify().setEnabled(true);

        service().notify(SCENE_ID, TENANT_ID, alert("critical"));

        verify(sender, never()).send(anyString(), anyMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsPayloadWithAlertFields() {
        properties.getAlertNotify().setEnabled(true);
        properties.getAlertNotify().setWebhookUrls(List.of(URL));
        properties.getAlertNotify().setCooldownSeconds(0);

        service().notify(SCENE_ID, TENANT_ID, alert("critical"));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(sender).send(eq(URL), captor.capture());
        Map<String, Object> payload = captor.getValue();
        assertEquals("twin.alert", payload.get("event"));
        assertEquals("100", payload.get("sceneId"));
        assertEquals("1", payload.get("tenantId"));
        assertEquals("color", payload.get("stateKey"));
        assertEquals("critical", payload.get("level"));
        assertEquals("warn", payload.get("previous"));
        assertEquals(82.5, payload.get("value"));
    }

    @Test
    void suppressesRepeatedAlertWithinCooldown() {
        properties.getAlertNotify().setEnabled(true);
        properties.getAlertNotify().setWebhookUrls(List.of(URL));

        TwinAlertNotifyService service = service();
        service.notify(SCENE_ID, TENANT_ID, alert("warn"));
        service.notify(SCENE_ID, TENANT_ID, alert("warn"));

        verify(sender, times(1)).send(eq(URL), anyMap());
    }

    @Test
    void skipsRecoveryLevel() {
        properties.getAlertNotify().setEnabled(true);
        properties.getAlertNotify().setWebhookUrls(List.of(URL));
        properties.getAlertNotify().setCooldownSeconds(0);

        service().notify(SCENE_ID, TENANT_ID, alert("normal"));

        verify(sender, never()).send(anyString(), any());
    }

    /**
     * 构造被测服务（同步执行器）。
     *
     * @return 外发服务
     */
    private TwinAlertNotifyService service() {
        return new TwinAlertNotifyService(properties, sender, Runnable::run);
    }

    /**
     * 构造告警条目。
     *
     * @param level 级别
     * @return 告警视图
     */
    private static BindingAlertVO alert(String level) {
        return BindingAlertVO.builder()
                .bindingId("1")
                .entityId("200")
                .entityCode("boiler-01")
                .stateKey("color")
                .value(82.5)
                .previous("warn")
                .level(level)
                .thresholds(List.of(new BigDecimal("50"), new BigDecimal("80")))
                .ts("2026-11-10T18:30:00")
                .build();
    }
}
