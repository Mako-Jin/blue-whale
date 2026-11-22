package com.bluewhale.pay.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@link HttpWhalePayClient} 单元测试：头部注入、幂等键生成、错误映射与重试边界。
 *
 * @author BlueWhale
 */
class HttpWhalePayClientTest {

    /** 支付服务测试地址 */
    private static final String BASE_URL = "http://pay.local";

    private WhalePayClientProperties properties;

    private MockRestServiceServer server;

    private HttpWhalePayClient client;

    @BeforeEach
    void setUp() {
        properties = new WhalePayClientProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setInternalToken("tok-1");
        properties.setTenantId(7L);
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpWhalePayClient(properties, builder.build());
    }

    @Test
    @DisplayName("创建支付单：自动带令牌/租户/链路头，缺单号时按业务键生成并解析视图")
    void shouldCreateWithGeneratedOrderNo() {
        server.expect(requestTo(BASE_URL + "/pay/v1/internal/order"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", "tok-1"))
                .andExpect(header("X-Tenant-Id", "7"))
                .andExpect(request -> {
                    String traceId = request.getHeaders().getFirst("X-Trace-Id");
                    assertTrue(traceId != null && traceId.length() == 32, "traceId 应为 32 位");
                })
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "\"orderNo\":\"MALL_ORDER-O-9\"")))
                .andRespond(withSuccess("{\"code\":0,\"message\":\"ok\",\"data\":"
                        + "{\"orderNo\":\"MALL_ORDER-O-9\",\"status\":\"CREATED\"}}",
                        MediaType.APPLICATION_JSON));

        PayOrderView view = client.create(new PayCreateCommand(null, "MALL_ORDER", "O-9",
                new BigDecimal("10.00"), "CNY", "MOCK", "http://biz.local/notify", null));

        assertEquals("MALL_ORDER-O-9", view.orderNo());
        assertEquals("CREATED", view.status());
        server.verify();
    }

    @Test
    @DisplayName("退款：未传 refundNo 时生成幂等键")
    void shouldGenerateRefundNo() {
        server.expect(requestTo(BASE_URL + "/pay/v1/internal/refund"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.refundNo").isNotEmpty())
                .andExpect(jsonPath("$.payOrderNo").value("PAY-1"))
                .andRespond(withSuccess("{\"code\":0,\"message\":\"ok\",\"data\":"
                        + "{\"refundNo\":\"RF-1\",\"status\":\"SUCCESS\"}}",
                        MediaType.APPLICATION_JSON));

        PayRefundView view = client.refund(new PayRefundCommand("PAY-1", null,
                new BigDecimal("5.00"), "用户退款"));

        assertEquals("RF-1", view.refundNo());
        server.verify();
    }

    @Test
    @DisplayName("业务错误：Result.code 非 0 直接抛异常且不重试")
    void shouldNotRetryBusinessError() {
        properties.setMaxRetries(2);
        server.expect(requestTo(BASE_URL + "/pay/v1/internal/order/PAY-1"))
                .andRespond(withSuccess("{\"code\":400,\"message\":\"单号已存在\",\"data\":null}",
                        MediaType.APPLICATION_JSON));

        PayClientException ex = assertThrows(PayClientException.class, () -> client.getByOrderNo("PAY-1"));

        assertEquals(400, ex.getCode());
        assertEquals("pay.client.biz-error", ex.getMessageKey());
        server.verify();
    }

    @Test
    @DisplayName("5xx：幂等接口自动重试一次")
    void shouldRetryOnServerError() {
        properties.setMaxRetries(1);
        server.expect(requestTo(BASE_URL + "/pay/v1/internal/order/PAY-1"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(requestTo(BASE_URL + "/pay/v1/internal/order/PAY-1"))
                .andRespond(withSuccess("{\"code\":0,\"message\":\"ok\",\"data\":"
                        + "{\"orderNo\":\"PAY-1\",\"status\":\"PAID\"}}", MediaType.APPLICATION_JSON));

        PayOrderView view = client.getByOrderNo("PAY-1");

        assertEquals("PAID", view.status());
        server.verify();
    }

    @Test
    @DisplayName("发起支付非幂等：5xx 不重试")
    void shouldNotRetryPay() {
        properties.setMaxRetries(2);
        server.expect(requestTo(BASE_URL + "/pay/v1/internal/order/PAY-1/pay"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(PayClientException.class, () -> client.pay("PAY-1"));
        server.verify();
    }

    @Test
    @DisplayName("本地参数校验：缺少单号与业务键直接失败，不发请求")
    void shouldRejectMissingOrderNo() {
        PayClientException ex = assertThrows(PayClientException.class,
                () -> client.create(new PayCreateCommand(null, "MALL_ORDER", null,
                        new BigDecimal("1.00"), "CNY", "MOCK", "http://biz.local/notify", null)));

        assertEquals(400, ex.getCode());
        assertEquals("pay.client.param-error", ex.getMessageKey());
    }
}
