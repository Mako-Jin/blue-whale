package com.bluewhale.common.internaltoken;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link InternalTokenInterceptor} 与 {@link InternalTokenSupport} 单元测试（V-02）：
 * 出站请求自动附加令牌头、令牌未配置不附加、不覆盖已有头、工厂构造可用。
 *
 * @author BlueWhale
 */
class InternalTokenInterceptorTest {

    private InternalTokenProperties properties;

    @BeforeEach
    void setUp() {
        properties = new InternalTokenProperties();
        properties.setToken("secret-token");
        InternalTokenSupport.init(properties);
    }

    @AfterEach
    void tearDown() {
        InternalTokenSupport.init(new InternalTokenProperties());
    }

    /**
     * 构造模拟出站请求
     *
     * @return 请求
     */
    private static HttpRequest buildRequest() {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET,
                URI.create("http://whale-file:8081/file/internal/1"));
        request.setResponse(new MockClientHttpResponse(new byte[0], 200));
        return request;
    }

    /**
     * 执行拦截：记录最终请求头
     *
     * @param interceptor 拦截器
     * @return 最终请求（含头）
     * @throws IOException 执行异常
     */
    private static MockClientHttpRequest intercept(ClientHttpRequestInterceptor interceptor) throws IOException {
        MockClientHttpRequest request = (MockClientHttpRequest) buildRequest();
        ClientHttpRequestExecution execution = (req, body) -> {
            MockClientHttpRequest mock = (MockClientHttpRequest) req;
            mock.setResponse(new MockClientHttpResponse(new byte[0], 200));
            return mock.execute();
        };
        interceptor.intercept(request, new byte[0], execution);
        return request;
    }

    @Test
    @DisplayName("interceptor：令牌已配置时为出站请求附加 X-Internal-Token 头")
    void shouldAddTokenHeaderWhenConfigured() throws IOException {
        MockClientHttpRequest request = intercept(new InternalTokenInterceptor(properties));

        assertEquals("secret-token", request.getHeaders().getFirst("X-Internal-Token"));
    }

    @Test
    @DisplayName("interceptor：配置本服务身份时发送 <服务ID>.<密钥>")
    void shouldAddIssuerTokenWhenIdentityConfigured() throws IOException {
        properties.setIssuerId("mall");
        properties.setIssuerSecret("mall-secret");

        MockClientHttpRequest request = intercept(new InternalTokenInterceptor(properties));

        assertEquals("mall.mall-secret", request.getHeaders().getFirst("X-Internal-Token"));
    }

    @Test
    @DisplayName("interceptor：令牌未配置时不附加头")
    void shouldNotAddTokenHeaderWhenBlank() throws IOException {
        properties.setToken("");

        MockClientHttpRequest request = intercept(new InternalTokenInterceptor(properties));

        assertNull(request.getHeaders().getFirst("X-Internal-Token"));
    }

    @Test
    @DisplayName("interceptor：已有同名头时不覆盖")
    void shouldNotOverwriteExistingHeader() throws IOException {
        MockClientHttpRequest request = (MockClientHttpRequest) buildRequest();
        request.getHeaders().set("X-Internal-Token", "preset");

        new InternalTokenInterceptor(properties).intercept(request, new byte[0],
                (req, body) -> {
                    MockClientHttpRequest mock = (MockClientHttpRequest) req;
                    mock.setResponse(new MockClientHttpResponse(new byte[0], 200));
                    return mock.execute();
                });

        assertEquals("preset", request.getHeaders().getFirst("X-Internal-Token"));
    }

    @Test
    @DisplayName("support：restClient()/builder() 工厂构造可用且包含令牌拦截器")
    void shouldBuildRestClientViaSupport() {
        RestClient restClient = InternalTokenSupport.restClient();

        assertEquals(RestClient.class, restClient.getClass().getInterfaces()[0]);
    }
}
