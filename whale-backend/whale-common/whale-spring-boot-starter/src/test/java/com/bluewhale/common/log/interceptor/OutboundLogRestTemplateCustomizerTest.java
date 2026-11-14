package com.bluewhale.common.log.interceptor;

import com.bluewhale.common.audit.support.SensitiveDataMasker;
import com.bluewhale.common.log.mapper.OutboundLogMapper;
import com.bluewhale.common.log.sink.LogPersistSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link OutboundLogRestTemplateCustomizer} 单元测试（REQ-SEC-006 A5）：
 * 请求工厂包装为可重复读、拦截器挂载顺序。
 *
 * @author BlueWhale
 */
class OutboundLogRestTemplateCustomizerTest {

    private final OutboundLogInterceptor interceptor = new OutboundLogInterceptor(
            mock(OutboundLogMapper.class), mock(SensitiveDataMasker.class), mock(LogPersistSupport.class));

    @Test
    @DisplayName("customize：请求工厂被包装为 BufferingClientHttpRequestFactory，拦截器挂到链首")
    void shouldWrapRequestFactoryAndRegisterInterceptor() {
        RestTemplate restTemplate = new RestTemplate();
        ClientHttpRequestFactory original = restTemplate.getRequestFactory();

        new OutboundLogRestTemplateCustomizer(interceptor).customize(restTemplate);

        // RestTemplate 存在拦截器时，getRequestFactory() 会被再包一层 InterceptingClientHttpRequestFactory，
        // 故需下钻一层确认真正被替换成 Buffering 工厂，否则「请求体可重复读」这条能力会静默丢失。
        Object wrapped = ReflectionTestUtils.getField(restTemplate.getRequestFactory(), "requestFactory");
        assertThat(wrapped).isInstanceOf(BufferingClientHttpRequestFactory.class).isNotSameAs(original);
        assertThat(restTemplate.getInterceptors()).hasSize(1).first().isSameAs(interceptor);
    }

    @Test
    @DisplayName("customize：请求工厂为 null 时跳过包装，仍挂载拦截器")
    void shouldTolerateNullRequestFactory() {
        RestTemplate restTemplate = new RestTemplate() {
            @Override
            public ClientHttpRequestFactory getRequestFactory() {
                return null;
            }
        };

        new OutboundLogRestTemplateCustomizer(interceptor).customize(restTemplate);

        assertThat(restTemplate.getInterceptors()).containsExactly(interceptor);
    }
}
