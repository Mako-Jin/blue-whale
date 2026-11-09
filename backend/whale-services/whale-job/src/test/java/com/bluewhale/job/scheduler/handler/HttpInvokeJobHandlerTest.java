package com.bluewhale.job.scheduler.handler;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@link HttpInvokeJobHandler} 单元测试。
 *
 * @author BlueWhale
 */
class HttpInvokeJobHandlerTest {

    /** 被测执行器 */
    private HttpInvokeJobHandler handler;

    /** Mock REST 服务端 */
    private MockRestServiceServer server;

    /** 服务发现桩 */
    private DiscoveryClient discoveryClient;

    /**
     * 初始化：绑定 Mock 服务端并注册 whale-crm 单实例
     */
    @BeforeEach
    void setUp() {
        discoveryClient = mock(DiscoveryClient.class);
        when(discoveryClient.getInstances("whale-crm")).thenReturn(List.of(
                new DefaultServiceInstance("whale-crm-1", "whale-crm", "127.0.0.1", 41007, false)));
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        handler = new HttpInvokeJobHandler(discoveryClient, builder);
    }

    /**
     * 成功路径：按参数发起 POST，业务码 0 时返回摘要
     *
     * @throws Exception 执行失败
     */
    @Test
    @DisplayName("业务码 0：返回含 data 的执行摘要")
    void shouldReturnSummaryOnSuccess() throws Exception {
        server.expect(requestTo("http://127.0.0.1:41007/crm/internal/analytics/snapshot"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":0,\"message\":\"ok\",\"data\":5}", MediaType.APPLICATION_JSON));

        String summary = handler.execute(
                "{\"service\":\"whale-crm\",\"method\":\"POST\",\"path\":\"/crm/internal/analytics/snapshot\"}");

        assertThat(summary).contains("whale-crm").contains("code=0").contains("data=5");
        server.verify();
    }

    /**
     * 业务码非 0：抛出业务异常
     */
    @Test
    @DisplayName("业务码非 0：抛出业务失败异常")
    void shouldThrowOnBusinessFailure() {
        server.expect(requestTo("http://127.0.0.1:41007/crm/internal/analytics/action-plan"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":500,\"message\":\"分析模块已禁用\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> handler.execute(
                "{\"service\":\"whale-crm\",\"path\":\"/crm/internal/analytics/action-plan\"}"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("分析模块已禁用");
        server.verify();
    }

    /**
     * 目标服务无可用实例
     */
    @Test
    @DisplayName("服务无实例：抛出无可用实例异常")
    void shouldThrowWhenNoInstance() {
        when(discoveryClient.getInstances("whale-ghost")).thenReturn(List.of());

        assertThatThrownBy(() -> handler.execute(
                "{\"service\":\"whale-ghost\",\"path\":\"/x\"}"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无可用实例");
    }

    /**
     * 参数缺 service/path 或非 JSON
     */
    @Test
    @DisplayName("参数非法：缺少 service/path 或非 JSON 时抛出")
    void shouldThrowOnInvalidParam() {
        assertThatThrownBy(() -> handler.execute("{\"path\":\"/x\"}"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("service");
        assertThatThrownBy(() -> handler.execute("not-json"))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> handler.execute(" "))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("参数为空");
    }
}
