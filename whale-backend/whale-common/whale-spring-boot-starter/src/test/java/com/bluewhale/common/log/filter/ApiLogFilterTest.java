package com.bluewhale.common.log.filter;

import com.bluewhale.common.audit.support.SensitiveDataMasker;
import com.bluewhale.common.log.entity.ApiLogRecord;
import com.bluewhale.common.log.mapper.ApiLogMapper;
import com.bluewhale.common.log.properties.ApiLogProperties;
import com.bluewhale.common.log.sink.LogPersistSupport;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 后端接口日志过滤器单测（REQ-SEC-006 A4 / checklist 8.1）。
 *
 * <p>用 {@link MockHttpServletRequest}/{@link MockHttpServletResponse} + 同步 sink（{@code task.run()}）
 * 覆盖：正常落库、排除路径跳过、采样率 0 跳过、超大 body 透传不丢、异步派发只落库一次（含请求体与最终响应）、
 * 幂等标记防重复、默认排除路径含上报接口。</p>
 *
 * @author BlueWhale
 */
class ApiLogFilterTest {

    /** 请求 URI（含设备查询路径） */
    private static final String NORMAL_URI = "/device/list";
    /** 前端上报接口路径（默认排除） */
    private static final String FRONTEND_REPORT_URI = "/audit/v1/log/frontend/batch";
    /** 业务模块名（回填 api_log.module 的配置值） */
    private static final String MODULE_NAME = "whale-auth";

    private final ApiLogProperties properties = new ApiLogProperties();
    private final ApiLogMapper apiLogMapper = mock(ApiLogMapper.class);
    private final LogPersistSupport persistSupport = new LogPersistSupport((task, channel) -> task.run());
    private final SensitiveDataMasker sensitiveDataMasker = new SensitiveDataMasker(JsonMapper.builder().build());
    private final ApiLogFilter filter =
            new ApiLogFilter(properties, persistSupport, apiLogMapper, sensitiveDataMasker);

    @Test
    @DisplayName("正常请求：组装并落库 api_log，入参脱敏")
    void shouldPersistRecordForNormalRequest() throws Exception {
        MockHttpServletRequest request = jsonRequest(NORMAL_URI,
                "{\"username\":\"alice\",\"password\":\"P@ssw0rd!\"}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, capturingChain(new AtomicReference<>()));

        ApiLogRecord record = captureSingleRecord();
        assertThat(record.getRequestUri()).isEqualTo(NORMAL_URI);
        assertThat(record.getHttpMethod()).isEqualTo("POST");
        assertThat(record.getUserAgent()).isEqualTo("junit");
        assertThat(record.getResponseCode()).isEqualTo(200);
        assertThat(record.getBizStatus()).isEqualTo(1);
        assertThat(record.getCostMs()).isNotNull();
        assertThat(record.getRequestParams()).contains("\"password\":\"******\"").doesNotContain("P@ssw0rd!");
    }

    @Test
    @DisplayName("模块名回填：api_log.module 落库值等于配置的模块名")
    void shouldFillModuleFromProperties() throws Exception {
        properties.setModule(MODULE_NAME);
        MockHttpServletRequest request = jsonRequest(NORMAL_URI, "{}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, capturingChain(new AtomicReference<>()));

        assertThat(captureSingleRecord().getModule()).isEqualTo(MODULE_NAME);
    }

    @Test
    @DisplayName("查询串脱敏：token/password 不复明文落库")
    void shouldMaskSensitiveQueryString() throws Exception {
        MockHttpServletRequest request = jsonRequest(NORMAL_URI, "{}");
        request.setQueryString("token=jwt-value&password=P%40ssw0rd&page=1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, capturingChain(new AtomicReference<>()));

        assertThat(captureSingleRecord().getQueryString())
                .isEqualTo("token=***&password=******&page=1")
                .doesNotContain("jwt-value")
                .doesNotContain("P@ssw0rd");
    }

    @Test
    @DisplayName("排除路径命中：shouldNotFilter 返回 true 且不落库")
    void shouldSkipExcludedPath() throws Exception {
        MockHttpServletRequest request = jsonRequest(FRONTEND_REPORT_URI, "{}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        assertThat(filter.shouldNotFilter(request)).isTrue();
        filter.doFilter(request, response, (req, resp) -> chainInvoked.set(true));

        assertThat(chainInvoked).isTrue();
        verify(apiLogMapper, never()).insert(any(ApiLogRecord.class));
    }

    @Test
    @DisplayName("采样率 0：不落库但仍放行")
    void shouldSkipWhenSampleRateZero() throws Exception {
        properties.setSampleRate(0.0D);
        MockHttpServletRequest request = jsonRequest(NORMAL_URI, "{}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, resp) -> chainInvoked.set(true));

        assertThat(chainInvoked).isTrue();
        verify(apiLogMapper, never()).insert(any(ApiLogRecord.class));
    }

    @Test
    @DisplayName("超大 body：不抛异常且下游读到全量字节")
    void shouldPassThroughOversizedBodyWithoutException() throws Exception {
        properties.setMaxBodyLength(8);
        byte[] oversized = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = jsonRequest(NORMAL_URI, oversized);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<byte[]> downstream = new AtomicReference<>();

        filter.doFilter(request, response, capturingChain(downstream));

        assertThat(downstream.get()).isEqualTo(oversized);
        captureSingleRecord();
    }

    @Test
    @DisplayName("异步派发：首次暂存不落库，ASYNC 派发按最终响应落库一次")
    void shouldRecordOnceOnAsyncDispatch() throws Exception {
        MockHttpServletRequest request = jsonRequest(NORMAL_URI, "{\"username\":\"alice\"}");
        request.setAsyncSupported(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, resp) -> {
            ((HttpServletRequest) req).getInputStream().readAllBytes();
            ((HttpServletRequest) req).startAsync();
        });

        assertThat(filter.shouldNotFilterAsyncDispatch()).isFalse();
        verify(apiLogMapper, never()).insert(any(ApiLogRecord.class));

        request.setDispatcherType(DispatcherType.ASYNC);
        filter.doFilter(request, response, (req, resp) -> {
            ((HttpServletResponse) resp).setContentType("application/json");
            java.io.PrintWriter writer = ((HttpServletResponse) resp).getWriter();
            writer.write("{\"code\":0,\"data\":[]}");
            writer.flush();
        });

        ApiLogRecord record = captureSingleRecord();
        assertThat(record.getRequestUri()).isEqualTo(NORMAL_URI);
        assertThat(record.getRequestParams()).contains("\"username\":\"alice\"");
        assertThat(record.getResponseBody()).contains("\"code\":0");
        assertThat(record.getResponseCode()).isEqualTo(200);
        verify(apiLogMapper, times(1)).insert(any(ApiLogRecord.class));
    }

    @Test
    @DisplayName("幂等标记：同一请求重复进入过滤器只落库一次")
    void shouldPersistOnceForSameRequest() throws Exception {
        MockHttpServletRequest request = jsonRequest(NORMAL_URI, "{}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, capturingChain(new AtomicReference<>()));
        filter.doFilter(request, response, (req, resp) -> { });

        verify(apiLogMapper, times(1)).insert(any(ApiLogRecord.class));
    }

    @Test
    @DisplayName("默认排除路径含前端上报接口，防上报回环")
    void shouldContainReportPathsInDefaultExcludePaths() {
        assertThat(properties.getExcludePaths())
                .contains("/audit/v1/log/frontend/**", "/audit/v1/log/behavior/**");
    }

    /**
     * 构造 JSON 请求
     *
     * @param uri 请求 URI
     * @param content 请求体文本
     * @return 模拟请求
     */
    private static MockHttpServletRequest jsonRequest(String uri, String content) {
        return jsonRequest(uri, content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 构造 JSON 请求（字节体）
     *
     * @param uri 请求 URI
     * @param content 请求体字节
     * @return 模拟请求
     */
    private static MockHttpServletRequest jsonRequest(String uri, byte[] content) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setContentType("application/json");
        request.setContent(content);
        request.addHeader(HttpHeaders.USER_AGENT, "junit");
        return request;
    }

    /**
     * 构造读全请求体并写 JSON 响应的过滤器链
     *
     * @param captured 请求体捕获器
     * @return 过滤器链
     */
    private static FilterChain capturingChain(AtomicReference<byte[]> captured) {
        return (request, response) -> {
            captured.set(((HttpServletRequest) request).getInputStream().readAllBytes());
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":0}");
        };
    }

    /**
     * 捕获单条落库记录
     *
     * @return 落库的接口日志记录
     */
    private ApiLogRecord captureSingleRecord() {
        ArgumentCaptor<ApiLogRecord> captor = ArgumentCaptor.forClass(ApiLogRecord.class);
        verify(apiLogMapper).insert(captor.capture());
        return captor.getValue();
    }
}
