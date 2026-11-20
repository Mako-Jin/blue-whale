package com.bluewhale.alert.controller;

import com.bluewhale.alert.config.AlertNotifyProperties;
import com.bluewhale.alert.dto.PrometheusWebhookPayload;
import com.bluewhale.alert.service.AlertRecordService;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AlertPrometheusController} 入站令牌校验单元测试。
 *
 * @author BlueWhale
 */
class AlertPrometheusControllerTest {

    private static final String TOKEN = "test-alert-token";

    private final AlertRecordService alertRecordService = mock(AlertRecordService.class);

    private final AlertPrometheusController controller =
            new AlertPrometheusController(alertRecordService, properties());

    @Test
    @DisplayName("X-Alert-Token 正确时落库并返回条数")
    void shouldAcceptHeaderToken() {
        PrometheusWebhookPayload payload = new PrometheusWebhookPayload();
        when(alertRecordService.ingest(payload)).thenReturn(2);

        Result<Integer> result = controller.receive(TOKEN, null, payload, null);

        assertEquals(2, result.getData());
        verify(alertRecordService).ingest(payload);
    }

    @Test
    @DisplayName("Authorization Bearer 正确时同样放行")
    void shouldAcceptBearerToken() {
        PrometheusWebhookPayload payload = new PrometheusWebhookPayload();
        when(alertRecordService.ingest(payload)).thenReturn(1);

        Result<Integer> result = controller.receive(null, "Bearer " + TOKEN, payload, null);

        assertEquals(1, result.getData());
    }

    @Test
    @DisplayName("令牌缺失或不匹配时返回 403 且不落库")
    void shouldRejectInvalidToken() {
        PrometheusWebhookPayload payload = new PrometheusWebhookPayload();

        BizException missing = assertThrows(BizException.class,
                () -> controller.receive(null, null, payload, null));
        BizException wrong = assertThrows(BizException.class,
                () -> controller.receive("wrong-token", null, payload, null));

        assertEquals(403, missing.getCode());
        assertEquals(403, wrong.getCode());
        verify(alertRecordService, never()).ingest(payload);
    }

    @Test
    @DisplayName("入站 JSON：Jackson 3（MVC 默认）可正确反序列化 Alertmanager 请求体")
    void shouldDeserializeAlertmanagerPayloadWithJackson3() {
        String json = """
                {"version":"4","status":"firing","alerts":[
                  {"status":"firing",
                   "labels":{"alertname":"cpu_high","severity":"critical","tenant_id":"7"},
                   "annotations":{"summary":"CPU 92%"},
                   "startsAt":"2026-10-25T10:00:00+08:00",
                   "fingerprint":"fp-1"}]}
                """;

        PrometheusWebhookPayload payload = JsonMapper.builder().build()
                .readValue(json, PrometheusWebhookPayload.class);

        assertEquals(1, payload.getAlerts().size());
        PrometheusWebhookPayload.PrometheusAlert alert = payload.getAlerts().get(0);
        assertEquals("cpu_high", alert.getLabels().get("alertname"));
        assertEquals("7", alert.getLabels().get("tenant_id"));
        assertEquals("CPU 92%", alert.getAnnotations().get("summary"));
        assertEquals("fp-1", alert.getFingerprint());
    }

    /**
     * 构造带令牌的配置。
     *
     * @return 通知配置
     */
    private static AlertNotifyProperties properties() {
        AlertNotifyProperties properties = new AlertNotifyProperties();
        properties.setWebhookToken(TOKEN);
        return properties;
    }
}
