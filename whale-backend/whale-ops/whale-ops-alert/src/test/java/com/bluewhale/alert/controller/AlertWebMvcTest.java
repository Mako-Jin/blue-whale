package com.bluewhale.alert.controller;

import com.bluewhale.alert.config.AlertNotifyProperties;
import com.bluewhale.alert.service.AlertRecordService;
import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 告警入站与元数据接口的 MockMvc 集成测试（standalone，不拉起容器）。
 *
 * <p>覆盖路由绑定、Jackson 反序列化、令牌守卫拦截与枚举下发，避免仅靠直接调用控制器
 * 方法而漏掉路径 / 请求头 / 请求体绑定问题。</p>
 *
 * @author BlueWhale
 */
class AlertWebMvcTest {

    private static final String TOKEN = "integration-token";

    private static final String PAYLOAD = """
            {"version":"4","status":"firing","alerts":[
              {"status":"firing",
               "labels":{"alertname":"cpu_high","severity":"critical"},
               "annotations":{"summary":"CPU 92%"},
               "startsAt":"2026-10-25T10:00:00+08:00"}]}
            """;

    private final AlertRecordService alertRecordService = mock(AlertRecordService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AlertNotifyProperties properties = new AlertNotifyProperties();
        properties.setWebhookToken(TOKEN);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AlertPrometheusController(alertRecordService, properties),
                new AlertMetaController()).build();
    }

    @Test
    @DisplayName("POST /alert/internal/prometheus：令牌正确时完成 JSON 绑定并返回条数")
    void shouldIngestWebhookViaHttp() throws Exception {
        when(alertRecordService.ingest(any())).thenReturn(1);

        mockMvc.perform(post("/alert/internal/prometheus")
                        .header("X-Alert-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(1));

        verify(alertRecordService).ingest(any());
    }

    @Test
    @DisplayName("POST /alert/internal/prometheus：缺少令牌时拒绝且不落库")
    void shouldRejectWebhookWithoutToken() {
        jakarta.servlet.ServletException exception = org.junit.jupiter.api.Assertions.assertThrows(
                jakarta.servlet.ServletException.class,
                () -> mockMvc.perform(post("/alert/internal/prometheus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD)));

        assertTrue(exception.getCause() instanceof BizException);
        verify(alertRecordService, never()).ingest(any());
    }

    @Test
    @DisplayName("GET /alert/meta/enums：下发级别、通道、运算符、状态与排序枚举")
    void shouldExposeEnums() throws Exception {
        mockMvc.perform(get("/alert/meta/enums"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.levels[0]").value("INFO"))
                .andExpect(jsonPath("$.data.channels[0]").value("EMAIL"))
                .andExpect(jsonPath("$.data.statuses[2]").value("SILENCED"));
    }
}
