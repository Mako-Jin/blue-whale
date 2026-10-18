package com.bluewhale.common.log.interceptor;

import com.bluewhale.common.audit.support.SensitiveDataMasker;
import com.bluewhale.common.log.entity.OutboundLogRecord;
import com.bluewhale.common.log.mapper.OutboundLogMapper;
import com.bluewhale.common.log.sink.LogPersistSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 出站三方调用日志拦截器单测（REQ-SEC-006 A5 / checklist 8.3）。
 *
 * <p>用 {@link MockRestServiceServer} + {@link SimpleClientHttpRequestFactory} 覆盖：正常 200 落库、
 * 服务端错误记录、超时异常记 {@code biz_status=0} 且原样抛、traceId 透传、请求/响应体敏感字段掩码。</p>
 *
 * @author BlueWhale
 */
class OutboundLogInterceptorTest {

    /** 出站目标地址 */
    private static final String TARGET_URL = "http://third-party.test/device";
    /** 网关注入的链路追踪ID */
    private static final String TRACE_ID = "trace-from-gateway";

    private final OutboundLogMapper outboundLogMapper = mock(OutboundLogMapper.class);
    private final SensitiveDataMasker sensitiveDataMasker = new SensitiveDataMasker(JsonMapper.builder().build());
    private final LogPersistSupport persistSupport = new LogPersistSupport((task, channel) -> task.run());
    private final OutboundLogInterceptor interceptor =
            new OutboundLogInterceptor(outboundLogMapper, sensitiveDataMasker, persistSupport);

    private RestTemplate restTemplate;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate(new SimpleClientHttpRequestFactory());
        restTemplate.getInterceptors().add(interceptor);
        server = MockRestServiceServer.createServer(restTemplate);
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("正常 200：落库且 cost_ms>=0、status=200、biz_status=1")
    void shouldRecordSuccessCall() {
        server.expect(requestTo(TARGET_URL)).andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        restTemplate.getForObject(TARGET_URL, String.class);
        server.verify();

        OutboundLogRecord record = captureSingleRecord();
        assertThat(record.getResponseCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(record.getBizStatus()).isEqualTo(1);
        assertThat(record.getCostMs()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(record.getTargetHost()).isEqualTo("third-party.test");
        assertThat(record.getTargetUri()).isEqualTo("/device");
        assertThat(record.getHttpMethod()).isEqualTo("GET");
    }

    @Test
    @DisplayName("服务端错误：记 status=500、biz_status=0")
    void shouldRecordServerErrorCall() {
        server.expect(requestTo(TARGET_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"error\":\"boom\"}"));

        assertThatThrownBy(() -> restTemplate.getForObject(TARGET_URL, String.class))
                .isInstanceOf(HttpServerErrorException.class);

        OutboundLogRecord record = captureSingleRecord();
        assertThat(record.getResponseCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(record.getBizStatus()).isEqualTo(0);
    }

    @Test
    @DisplayName("读取超时：记 biz_status=0 且异常原样抛出")
    void shouldRecordTimeoutAndRethrowException() {
        server.expect(requestTo(TARGET_URL))
                .andRespond(request -> {
                    throw new SocketTimeoutException("read timed out");
                });

        assertThatThrownBy(() -> restTemplate.getForObject(TARGET_URL, String.class))
                .isInstanceOf(ResourceAccessException.class)
                .hasRootCauseInstanceOf(SocketTimeoutException.class);

        OutboundLogRecord record = captureSingleRecord();
        assertThat(record.getBizStatus()).isEqualTo(0);
        assertThat(record.getResponseCode()).isNull();
        assertThat(record.getErrorMsg()).contains("SocketTimeoutException").contains("read timed out");
    }

    @Test
    @DisplayName("traceId 透传：优先取网关注入头")
    void shouldPropagateTraceIdFromRequestHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", TRACE_ID);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        server.expect(requestTo(TARGET_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        restTemplate.getForObject(TARGET_URL, String.class);

        OutboundLogRecord record = captureSingleRecord();
        assertThat(record.getTraceId()).isEqualTo(TRACE_ID);
    }

    @Test
    @DisplayName("无请求上下文：生成 32 位 traceId")
    void shouldGenerateTraceIdWithoutRequestContext() {
        server.expect(requestTo(TARGET_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        restTemplate.getForObject(TARGET_URL, String.class);

        assertThat(captureSingleRecord().getTraceId()).hasSize(32);
    }

    @Test
    @DisplayName("敏感字段掩码：请求体 password 与响应体 token 均脱敏")
    void shouldMaskSensitiveFieldsInRequestAndResponse() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity =
                new HttpEntity<>("{\"username\":\"alice\",\"password\":\"P@ssw0rd!\"}", headers);
        server.expect(requestTo(TARGET_URL))
                .andRespond(withSuccess("{\"token\":\"secret-token\"}", MediaType.APPLICATION_JSON));

        restTemplate.postForObject(TARGET_URL, entity, String.class);

        OutboundLogRecord record = captureSingleRecord();
        assertThat(record.getRequestParams()).contains("\"password\":\"******\"").doesNotContain("P@ssw0rd!");
        assertThat(record.getResponseBody()).contains("\"token\":\"***\"").doesNotContain("secret-token");
    }

    /**
     * 捕获单条出站日志记录
     *
     * @return 落库的出站日志记录
     */
    private OutboundLogRecord captureSingleRecord() {
        ArgumentCaptor<OutboundLogRecord> captor = ArgumentCaptor.forClass(OutboundLogRecord.class);
        verify(outboundLogMapper).insert(captor.capture());
        return captor.getValue();
    }
}
