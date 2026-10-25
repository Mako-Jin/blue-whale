package com.bluewhale.alert.controller;

import com.bluewhale.alert.config.AlertNotifyProperties;
import com.bluewhale.alert.dto.PrometheusWebhookPayload;
import com.bluewhale.alert.service.AlertRecordService;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

        Result<Integer> result = controller.receive(TOKEN, null, payload);

        assertEquals(2, result.getData());
        verify(alertRecordService).ingest(payload);
    }

    @Test
    @DisplayName("Authorization Bearer 正确时同样放行")
    void shouldAcceptBearerToken() {
        PrometheusWebhookPayload payload = new PrometheusWebhookPayload();
        when(alertRecordService.ingest(payload)).thenReturn(1);

        Result<Integer> result = controller.receive(null, "Bearer " + TOKEN, payload);

        assertEquals(1, result.getData());
    }

    @Test
    @DisplayName("令牌缺失或不匹配时返回 403 且不落库")
    void shouldRejectInvalidToken() {
        PrometheusWebhookPayload payload = new PrometheusWebhookPayload();

        BizException missing = assertThrows(BizException.class,
                () -> controller.receive(null, null, payload));
        BizException wrong = assertThrows(BizException.class,
                () -> controller.receive("wrong-token", null, payload));

        assertEquals(403, missing.getCode());
        assertEquals(403, wrong.getCode());
        verify(alertRecordService, never()).ingest(payload);
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
