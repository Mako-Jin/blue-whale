package com.bluewhale.mes.dashboard.service;

import com.bluewhale.mes.dashboard.dto.MesDashboardStreamRequest;
import com.bluewhale.mes.dashboard.vo.MesDashboardVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MES 综合看板 SSE 推送服务单元测试（M5-B）。
 *
 * <p>验证推送失败（连接未初始化/客户端断开）不向调用方抛出异常，
 * 由 {@code completeWithError} 走清理回调终止连接。</p>
 *
 * @author BlueWhale
 */
class MesDashboardStreamServiceTest {

    @Test
    @DisplayName("推送失败不抛出异常，由清理回调终止连接")
    void pushOnceShouldSwallowSendFailure() {
        MesDashboardService dashboardService = mock(MesDashboardService.class);
        when(dashboardService.dashboard(any(), any(), any())).thenReturn(new MesDashboardVO());
        MesDashboardStreamService service = new MesDashboardStreamService(dashboardService, 10);

        // 未初始化的 SseEmitter send 会抛 IllegalStateException，推送应自行消化
        SseEmitter emitter = new SseEmitter(0L);
        assertDoesNotThrow(() -> service.pushOnce("sse-1", emitter, new MesDashboardStreamRequest()));
    }
}
