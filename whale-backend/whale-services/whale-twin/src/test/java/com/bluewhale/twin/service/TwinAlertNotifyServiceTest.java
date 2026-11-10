package com.bluewhale.twin.service;

import com.bluewhale.twin.config.TwinProperties;
import com.bluewhale.twin.dto.BindingAlertVO;
import com.bluewhale.twin.entity.TwinAlertDispatch;
import com.bluewhale.twin.support.TwinAlertRecipientResolver;
import com.bluewhale.twin.support.TwinAlertWebhookSender;
import com.bluewhale.twin.support.TwinMessageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 告警外发服务单测：开关短路、载荷组包、冷却抑制、站内信通道与投递留痕。
 *
 * <p>测试注入同步执行器（{@code Runnable::run}），避免异步断言竞态。</p>
 *
 * @author BlueWhale
 */
class TwinAlertNotifyServiceTest {

    private static final Long SCENE_ID = 100L;

    private static final Long TENANT_ID = 1L;

    private static final Long ALERT_ID = 9001L;

    private static final Long USER_ID = 7L;

    private static final String URL = "https://hook.example.com/twin/alert";

    private TwinProperties properties;

    private TwinAlertWebhookSender sender;

    private TwinMessageClient messageClient;

    private TwinAlertRecipientResolver recipientResolver;

    private TwinAlertDispatchService dispatchService;

    @BeforeEach
    void setUp() {
        properties = new TwinProperties();
        sender = mock(TwinAlertWebhookSender.class);
        messageClient = mock(TwinMessageClient.class);
        recipientResolver = mock(TwinAlertRecipientResolver.class);
        dispatchService = mock(TwinAlertDispatchService.class);
        when(recipientResolver.resolve(SCENE_ID))
                .thenReturn(new TwinAlertRecipientResolver.Recipients(List.of(USER_ID), "示范场景"));
    }

    @Test
    void doesNothingWhenAllChannelsOff() {
        properties.getAlertNotify().setWebhookUrls(List.of(URL));

        service().notify(SCENE_ID, TENANT_ID, ALERT_ID, alert("critical"));

        verify(sender, never()).send(anyString(), anyMap());
        verify(messageClient, never()).sendInapp(any(), anyString(), anyString(), anyString(),
                anyMap(), anyList());
    }

    @Test
    void doesNothingWhenWebhookEnabledButNoUrl() {
        properties.getAlertNotify().setEnabled(true);

        service().notify(SCENE_ID, TENANT_ID, ALERT_ID, alert("critical"));

        verify(sender, never()).send(anyString(), anyMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsPayloadWithAlertFields() {
        properties.getAlertNotify().setEnabled(true);
        properties.getAlertNotify().setWebhookUrls(List.of(URL));
        properties.getAlertNotify().setCooldownSeconds(0);

        service().notify(SCENE_ID, TENANT_ID, ALERT_ID, alert("critical"));

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
    void sendsInappWithRecipientsAndRecordsSuccess() {
        properties.getAlertNotify().setInappEnabled(true);
        properties.getAlertNotify().setCooldownSeconds(0);
        when(messageClient.sendInapp(any(), anyString(), anyString(), anyString(), anyMap(), anyList()))
                .thenReturn(new TwinMessageClient.SendOutcome(555L, "SENT", null));

        service().notify(SCENE_ID, TENANT_ID, ALERT_ID, alert("critical"));

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(messageClient).sendInapp(eq(TENANT_ID), eq("twin-alert-9001"), title.capture(),
                content.capture(), anyMap(), eq(List.of(USER_ID)));
        assertTrue(title.getValue().contains("严重"));
        assertTrue(title.getValue().contains("示范场景"));
        assertTrue(content.getValue().contains("boiler-01.color"));

        TwinAlertDispatch dispatch = captureDispatch();
        assertEquals(TwinAlertDispatchService.CHANNEL_INAPP, dispatch.getChannel());
        assertEquals(TwinAlertDispatchService.STATUS_SUCCESS, dispatch.getStatus());
        assertEquals(555L, dispatch.getMessageId());
        assertEquals(ALERT_ID, dispatch.getAlertId());
        assertEquals(TENANT_ID, dispatch.getTenantId());
    }

    @Test
    void skipsInappWhenNoRecipient() {
        properties.getAlertNotify().setInappEnabled(true);
        properties.getAlertNotify().setCooldownSeconds(0);
        when(recipientResolver.resolve(SCENE_ID))
                .thenReturn(new TwinAlertRecipientResolver.Recipients(List.of(), null));

        service().notify(SCENE_ID, TENANT_ID, ALERT_ID, alert("warn"));

        verify(messageClient, never()).sendInapp(any(), anyString(), anyString(), anyString(),
                anyMap(), anyList());
        TwinAlertDispatch dispatch = captureDispatch();
        assertEquals(TwinAlertDispatchService.STATUS_SKIPPED, dispatch.getStatus());
        assertEquals("无接收人", dispatch.getTarget());
    }

    @Test
    void recordsFailedInappDispatch() {
        properties.getAlertNotify().setInappEnabled(true);
        properties.getAlertNotify().setCooldownSeconds(0);
        when(messageClient.sendInapp(any(), anyString(), anyString(), anyString(), anyMap(), anyList()))
                .thenReturn(new TwinMessageClient.SendOutcome(null, null, "connect timed out"));

        service().notify(SCENE_ID, TENANT_ID, ALERT_ID, alert("warn"));

        TwinAlertDispatch dispatch = captureDispatch();
        assertEquals(TwinAlertDispatchService.STATUS_FAILED, dispatch.getStatus());
        assertEquals("connect timed out", dispatch.getErrorMsg());
    }

    @Test
    void recordsWebhookDispatchResult() {
        properties.getAlertNotify().setEnabled(true);
        properties.getAlertNotify().setWebhookUrls(List.of(URL));
        properties.getAlertNotify().setCooldownSeconds(0);
        when(sender.send(eq(URL), anyMap())).thenReturn(true);

        service().notify(SCENE_ID, TENANT_ID, ALERT_ID, alert("critical"));

        TwinAlertDispatch dispatch = captureDispatch();
        assertEquals(TwinAlertDispatchService.CHANNEL_WEBHOOK, dispatch.getChannel());
        assertEquals(TwinAlertDispatchService.STATUS_SUCCESS, dispatch.getStatus());
        assertEquals(URL, dispatch.getTarget());
    }

    @Test
    void suppressesRepeatedAlertWithinCooldown() {
        properties.getAlertNotify().setEnabled(true);
        properties.getAlertNotify().setWebhookUrls(List.of(URL));

        TwinAlertNotifyService service = service();
        service.notify(SCENE_ID, TENANT_ID, ALERT_ID, alert("warn"));
        service.notify(SCENE_ID, TENANT_ID, ALERT_ID, alert("warn"));

        verify(sender, times(1)).send(eq(URL), anyMap());
    }

    @Test
    void skipsRecoveryLevel() {
        properties.getAlertNotify().setEnabled(true);
        properties.getAlertNotify().setWebhookUrls(List.of(URL));
        properties.getAlertNotify().setCooldownSeconds(0);

        service().notify(SCENE_ID, TENANT_ID, null, alert("normal"));

        verify(sender, never()).send(anyString(), any());
        verify(dispatchService, never()).record(any());
    }

    /**
     * 构造被测服务（同步执行器）。
     *
     * @return 外发服务
     */
    private TwinAlertNotifyService service() {
        return new TwinAlertNotifyService(properties, sender, messageClient, recipientResolver,
                dispatchService, Runnable::run);
    }

    /**
     * 抓取唯一一条投递记录。
     *
     * @return 投递记录
     */
    private TwinAlertDispatch captureDispatch() {
        ArgumentCaptor<TwinAlertDispatch> captor = ArgumentCaptor.forClass(TwinAlertDispatch.class);
        verify(dispatchService).record(captor.capture());
        return captor.getValue();
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
