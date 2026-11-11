package com.bluewhale.twin.service;

import com.bluewhale.twin.config.TwinProperties;
import com.bluewhale.twin.dto.BindingAlertVO;
import com.bluewhale.twin.entity.TwinAlertDispatch;
import com.bluewhale.twin.entity.TwinScene;
import com.bluewhale.twin.mapper.TwinSceneMapper;
import com.bluewhale.twin.support.TwinAlertRecipientResolver;
import com.bluewhale.twin.support.TwinAlertWebhookSender;
import com.bluewhale.twin.support.TwinMessageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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

    private TwinSceneMapper sceneMapper;

    private TwinAlertNotifySettingsService settingsService;

    @BeforeEach
    void setUp() {
        properties = new TwinProperties();
        sender = mock(TwinAlertWebhookSender.class);
        messageClient = mock(TwinMessageClient.class);
        recipientResolver = mock(TwinAlertRecipientResolver.class);
        dispatchService = mock(TwinAlertDispatchService.class);
        sceneMapper = mock(TwinSceneMapper.class);
        // 用真实设置服务：场景级收紧逻辑（关通道/抬门槛）就是被测行为，mock 掉会失去意义
        settingsService = new TwinAlertNotifySettingsService(sceneMapper, properties);
        when(recipientResolver.resolve(SCENE_ID))
                .thenReturn(new TwinAlertRecipientResolver.Recipients(List.of(USER_ID), "示范场景"));
        // Wave 6.17c：偏好解析默认桩为 null（fail-open 全部放行），仅偏好专项用例会覆盖
        when(messageClient.resolveInappRecipients(any(), anyList(), any(), anyBoolean()))
                .thenReturn(null);
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
    void skipsInappWhenPreferenceFiltersAllRecipients() {
        // Wave 6.17c：偏好把唯一接收人也剔掉（渠道关闭/静默）→ 不外发，记 SKIPPED
        properties.getAlertNotify().setInappEnabled(true);
        properties.getAlertNotify().setCooldownSeconds(0);
        when(messageClient.resolveInappRecipients(any(), anyList(), any(), anyBoolean()))
                .thenReturn(List.of());

        service().notify(SCENE_ID, TENANT_ID, ALERT_ID, alert("warn"));

        verify(messageClient, never()).sendInapp(any(), anyString(), anyString(), anyString(),
                anyMap(), anyList());
        TwinAlertDispatch dispatch = captureDispatch();
        assertEquals(TwinAlertDispatchService.STATUS_SKIPPED, dispatch.getStatus());
        assertEquals("userIds=0", dispatch.getTarget());
    }

    @Test
    void skipsInappWhenPreferenceFiltersSomeRecipients() {
        // Wave 6.17c：偏好放行部分接收人 → 只对剩余用户外发
        properties.getAlertNotify().setInappEnabled(true);
        properties.getAlertNotify().setCooldownSeconds(0);
        when(recipientResolver.resolve(SCENE_ID))
                .thenReturn(new TwinAlertRecipientResolver.Recipients(List.of(USER_ID, 999L), "示范场景"));
        when(messageClient.resolveInappRecipients(any(), anyList(), any(), anyBoolean()))
                .thenReturn(List.of(USER_ID));
        when(messageClient.sendInapp(any(), anyString(), anyString(), anyString(), anyMap(), anyList()))
                .thenReturn(new TwinMessageClient.SendOutcome(556L, "SENT", null));

        service().notify(SCENE_ID, TENANT_ID, ALERT_ID, alert("warn"));

        verify(messageClient).sendInapp(eq(TENANT_ID), anyString(), anyString(), anyString(),
                anyMap(), eq(List.of(USER_ID)));
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

    @Test
    void sceneCanDisableWebhookChannelOnly() {
        properties.getAlertNotify().setEnabled(true);
        properties.getAlertNotify().setWebhookUrls(List.of(URL));
        properties.getAlertNotify().setInappEnabled(true);
        properties.getAlertNotify().setCooldownSeconds(0);
        when(sceneMapper.selectById(SCENE_ID))
                .thenReturn(sceneWith("{\"alertNotify\":{\"webhookEnabled\":false}}"));
        when(messageClient.sendInapp(any(), anyString(), anyString(), anyString(), anyMap(), anyList()))
                .thenReturn(new TwinMessageClient.SendOutcome(556L, "SENT", null));

        service().notify(SCENE_ID, TENANT_ID, ALERT_ID, alert("critical"));

        // webhook 被场景关掉，站内信仍发（M2 单向收紧语义）
        verify(sender, never()).send(anyString(), any());
        verify(messageClient).sendInapp(any(), anyString(), anyString(), anyString(), anyMap(), anyList());
    }

    @Test
    void sceneRaisesMinLevelAboveGlobal() {
        properties.getAlertNotify().setEnabled(true);
        properties.getAlertNotify().setWebhookUrls(List.of(URL));
        properties.getAlertNotify().setCooldownSeconds(0);
        when(sceneMapper.selectById(SCENE_ID))
                .thenReturn(sceneWith("{\"alertNotify\":{\"minLevel\":\"critical\"}}"));

        service().notify(SCENE_ID, TENANT_ID, ALERT_ID, alert("warn"));

        // 全局门槛是 warn，但场景抬到 critical → warn 不外发
        verify(sender, never()).send(anyString(), any());

        service().notify(SCENE_ID, TENANT_ID, ALERT_ID, alert("critical"));

        verify(sender, times(1)).send(eq(URL), any());
    }

    @Test
    void sendsRecoveryAfterAlertWasNotified() {
        properties.getAlertNotify().setEnabled(true);
        properties.getAlertNotify().setWebhookUrls(List.of(URL));
        properties.getAlertNotify().setCooldownSeconds(0);

        TwinAlertNotifyService service = service();
        service.notify(SCENE_ID, TENANT_ID, ALERT_ID, alert("warn"));
        service.notify(SCENE_ID, TENANT_ID, null, alert("normal"));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(sender, times(2)).send(eq(URL), captor.capture());
        Map<String, Object> recovery = captor.getAllValues().get(1);
        assertEquals("twin.alert.recovered", recovery.get("event"));
        assertEquals("warn", recovery.get("recoveredFrom"));
        assertEquals("normal", recovery.get("level"));

        ArgumentCaptor<TwinAlertDispatch> dispatches =
                ArgumentCaptor.forClass(TwinAlertDispatch.class);
        verify(dispatchService, times(2)).record(dispatches.capture());
        assertEquals("normal", dispatches.getAllValues().get(1).getLevel());
    }

    @Test
    void skipsRecoveryForAlertBelowThreshold() {
        properties.getAlertNotify().setEnabled(true);
        properties.getAlertNotify().setWebhookUrls(List.of(URL));
        properties.getAlertNotify().setMinLevel("critical");
        properties.getAlertNotify().setCooldownSeconds(0);
        when(sceneMapper.selectById(SCENE_ID))
                .thenReturn(sceneWith("{\"alertNotify\":{\"minLevel\":\"critical\"}}"));

        TwinAlertNotifyService service = service();
        // warn 被门槛拦下（从未通知出去），恢复时不该凭空发一条
        service.notify(SCENE_ID, TENANT_ID, ALERT_ID, alert("warn"));
        service.notify(SCENE_ID, TENANT_ID, null, alert("normal"));

        verify(sender, never()).send(anyString(), any());
        verify(dispatchService, never()).record(any());
    }

    @Test
    void skipsRecoveryWhenGloballyDisabled() {
        properties.getAlertNotify().setEnabled(true);
        properties.getAlertNotify().setWebhookUrls(List.of(URL));
        properties.getAlertNotify().setRecoveryEnabled(false);
        properties.getAlertNotify().setCooldownSeconds(0);

        TwinAlertNotifyService service = service();
        service.notify(SCENE_ID, TENANT_ID, ALERT_ID, alert("warn"));
        service.notify(SCENE_ID, TENANT_ID, null, alert("normal"));

        verify(sender, times(1)).send(eq(URL), any());
    }

    @Test
    void sceneCanDisableRecoveryOnly() {
        properties.getAlertNotify().setEnabled(true);
        properties.getAlertNotify().setWebhookUrls(List.of(URL));
        properties.getAlertNotify().setCooldownSeconds(0);
        when(sceneMapper.selectById(SCENE_ID))
                .thenReturn(sceneWith("{\"alertNotify\":{\"recoveryEnabled\":false}}"));

        TwinAlertNotifyService service = service();
        service.notify(SCENE_ID, TENANT_ID, ALERT_ID, alert("warn"));
        service.notify(SCENE_ID, TENANT_ID, null, alert("normal"));

        // 告警照发，恢复被场景关掉
        verify(sender, times(1)).send(eq(URL), any());
    }

    @Test
    void quietHoursSuppressWarnButLetCriticalThrough() {
        TwinProperties.AlertNotify config = properties.getAlertNotify();
        config.setEnabled(true);
        config.setWebhookUrls(List.of(URL));
        config.setCooldownSeconds(0);
        String[] window = windowAroundNow();
        config.setQuietHoursStart(window[0]);
        config.setQuietHoursEnd(window[1]);

        TwinAlertNotifyService service = service();
        service.notify(SCENE_ID, TENANT_ID, ALERT_ID, alert("warn"));
        service.notify(SCENE_ID, TENANT_ID, ALERT_ID, alert("critical"));

        // 静默期内预警被拦、严重告警穿透（默认 bypass）
        verify(sender, times(1)).send(eq(URL), any());
    }

    @Test
    void quietHoursWithoutBypassSuppressCriticalToo() {
        TwinProperties.AlertNotify config = properties.getAlertNotify();
        config.setEnabled(true);
        config.setWebhookUrls(List.of(URL));
        config.setCooldownSeconds(0);
        config.setQuietHoursCriticalBypass(false);
        String[] window = windowAroundNow();
        config.setQuietHoursStart(window[0]);
        config.setQuietHoursEnd(window[1]);

        service().notify(SCENE_ID, TENANT_ID, ALERT_ID, alert("critical"));

        verify(sender, never()).send(anyString(), any());
    }

    @Test
    void sceneQuietWindowAddsSilenceOnTopOfGlobal() {
        TwinProperties.AlertNotify config = properties.getAlertNotify();
        config.setEnabled(true);
        config.setWebhookUrls(List.of(URL));
        config.setCooldownSeconds(0);
        String[] window = windowAroundNow();
        when(sceneMapper.selectById(SCENE_ID)).thenReturn(sceneWith(
                "{\"alertNotify\":{\"quietStart\":\"" + window[0] + "\",\"quietEnd\":\""
                        + window[1] + "\"}}"));

        service().notify(SCENE_ID, TENANT_ID, ALERT_ID, alert("warn"));

        // 全局没配静默，场景自己加的窗口也要生效（场景只能加严）
        verify(sender, never()).send(anyString(), any());
    }

    /**
     * 取一个「包含当前时刻」的静默窗口（±1 小时，自动处理跨零点）。
     *
     * @return 长度为 2 的数组：[开始, 结束]，格式 HH:mm
     */
    private static String[] windowAroundNow() {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("HH:mm");
        LocalTime now = LocalTime.now();
        return new String[]{format.format(now.minusHours(1)), format.format(now.plusHours(1))};
    }

    /**
     * 构造被测服务（同步执行器）。
     *
     * @return 外发服务
     */
    private TwinAlertNotifyService service() {
        return new TwinAlertNotifyService(properties, sender, messageClient, recipientResolver,
                dispatchService, settingsService, Runnable::run);
    }

    /**
     * 造一个带通知配置的场景行。
     *
     * @param sceneConfig sceneConfig JSON
     * @return 场景实体
     */
    private TwinScene sceneWith(String sceneConfig) {
        TwinScene scene = new TwinScene();
        scene.setId(SCENE_ID);
        scene.setName("示范场景");
        scene.setSceneConfig(sceneConfig);
        scene.setCreatedBy(USER_ID);
        return scene;
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
