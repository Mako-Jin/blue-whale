package com.bluewhale.audit.controller;

import com.bluewhale.audit.dto.ApiLogQueryRequest;
import com.bluewhale.audit.dto.LogBatchRequest;
import com.bluewhale.audit.dto.LogItem;
import com.bluewhale.audit.dto.OutboundLogQueryRequest;
import com.bluewhale.audit.service.ApiLogService;
import com.bluewhale.audit.service.BehaviorLogService;
import com.bluewhale.audit.service.FrontendLogService;
import com.bluewhale.audit.service.OutboundLogService;
import com.bluewhale.audit.vo.ApiLogVO;
import com.bluewhale.audit.vo.OutboundLogVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.WebHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 前端日志接收接口单测（REQ-SEC-006 / checklist 8.5）。
 *
 * <p>用 Mockito mock 服务层（不拉起全上下文）验证：批量上限与空集抛 {@code PARAM_ERROR}、
 * 合法批量调用 {@code saveBatch} 并返回成功、{@code X-User-Id} 头回填 {@code user_id}、
 * 上报接口无鉴权上下文亦可放行（{@code permitAll}）。</p>
 *
 * @author BlueWhale
 */
class LogIngestControllerTest {

    /** 批量上限（超出即拒绝） */
    private static final int OVER_LIMIT_SIZE = 51;
    /** 网关注入的用户ID */
    private static final String GATEWAY_USER_ID = "1001";
    /** 上报体声明的（不可信）用户ID */
    private static final long UNTRUSTED_USER_ID = 9999L;

    private final FrontendLogService frontendLogService = mock(FrontendLogService.class);
    private final BehaviorLogService behaviorLogService = mock(BehaviorLogService.class);
    private final ApiLogService apiLogService = mock(ApiLogService.class);
    private final OutboundLogService outboundLogService = mock(OutboundLogService.class);
    private final LogIngestController controller = new LogIngestController(
            frontendLogService, behaviorLogService, apiLogService, outboundLogService);

    @Test
    @DisplayName("items 为空：抛 PARAM_ERROR 且不调用服务")
    void shouldRejectEmptyItems() {
        LogBatchRequest request = new LogBatchRequest();
        request.setItems(List.of());

        assertThatThrownBy(() -> controller.frontendBatch(request, new MockHttpServletRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
        verify(frontendLogService, never()).saveBatch(any(), any());
    }

    @Test
    @DisplayName("items 为 null：抛 PARAM_ERROR")
    void shouldRejectNullItems() {
        LogBatchRequest request = new LogBatchRequest();

        assertThatThrownBy(() -> controller.behaviorBatch(request, new MockHttpServletRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("items 超过 50：抛 PARAM_ERROR")
    void shouldRejectOversizedBatch() {
        LogBatchRequest request = new LogBatchRequest();
        request.setItems(IntStream.range(0, OVER_LIMIT_SIZE).mapToObj(index -> new LogItem()).toList());

        assertThatThrownBy(() -> controller.behaviorBatch(request, new MockHttpServletRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
        verify(behaviorLogService, never()).saveBatch(any(), any());
    }

    @Test
    @DisplayName("合法批量：调用 saveBatch 且返回成功（上报体 userId 兜底）")
    void shouldSubmitValidBatchWithFallbackUserId() {
        LogBatchRequest request = batchOf(2);

        Result<Void> result = controller.frontendBatch(request, new MockHttpServletRequest());

        assertThat(result.getCode()).isEqualTo(ErrorCode.SUCCESS.getCode());
        verify(frontendLogService).saveBatch(request, UNTRUSTED_USER_ID);
    }

    @Test
    @DisplayName("X-User-Id 头回填：忽略上报体 userId")
    void shouldBackfillUserIdFromGatewayHeader() {
        LogBatchRequest request = batchOf(1);
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader(WebHeaders.USER_ID, GATEWAY_USER_ID);

        controller.frontendBatch(request, httpRequest);

        verify(frontendLogService).saveBatch(request, Long.valueOf(GATEWAY_USER_ID));
        verify(frontendLogService, never()).saveBatch(any(), eq(UNTRUSTED_USER_ID));
    }

    @Test
    @DisplayName("上报接口 permitAll：无鉴权上下文亦返回成功")
    void shouldAcceptReportWithoutAuthentication() {
        Result<Void> result = controller.behaviorBatch(batchOf(1), new MockHttpServletRequest());

        assertThat(result.getCode()).isEqualTo(ErrorCode.SUCCESS.getCode());
        verify(behaviorLogService).saveBatch(any(), any());
    }

    @Test
    @DisplayName("HTTP 上报：无鉴权亦返回 200")
    void shouldReportBatchOverHttpWithoutAuthentication() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        LogBatchRequest request = batchOf(1);
        String body = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(request);

        mockMvc.perform(post("/audit/log/frontend/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(frontendLogService).saveBatch(any(), eq(UNTRUSTED_USER_ID));
    }

    @Test
    @DisplayName("接口日志分页（通道①）：委托 ApiLogService 并原样返回分页结果")
    void shouldDelegateApiPageToService() {
        ApiLogQueryRequest request = new ApiLogQueryRequest();
        PageResult<ApiLogVO> page = PageResult.of(List.of(), 0L, 1L, 10L);
        when(apiLogService.page(request)).thenReturn(page);

        Result<PageResult<ApiLogVO>> result = controller.apiPage(request);

        assertThat(result.getData()).isSameAs(page);
        verify(apiLogService).page(request);
    }

    @Test
    @DisplayName("出站日志分页（通道②）：委托 OutboundLogService 并原样返回分页结果")
    void shouldDelegateOutboundPageToService() {
        OutboundLogQueryRequest request = new OutboundLogQueryRequest();
        PageResult<OutboundLogVO> page = PageResult.of(List.of(), 0L, 1L, 10L);
        when(outboundLogService.page(request)).thenReturn(page);

        Result<PageResult<OutboundLogVO>> result = controller.outboundPage(request);

        assertThat(result.getData()).isSameAs(page);
        verify(outboundLogService).page(request);
    }

    /**
     * 构造指定条目数的批量上报请求（固定不可信 userId）
     *
     * @param size 条目数
     * @return 批量上报请求
     */
    private static LogBatchRequest batchOf(int size) {
        LogBatchRequest request = new LogBatchRequest();
        request.setUserId(UNTRUSTED_USER_ID);
        request.setItems(IntStream.range(0, size).mapToObj(index -> new LogItem()).toList());
        return request;
    }
}
