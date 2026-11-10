package com.bluewhale.common.log.interceptor;

import org.springframework.boot.restclient.RestTemplateCustomizer;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 出站日志 RestTemplate 定制器（REQ-SEC-006 A5）。
 *
 * <p>由 {@code RestTemplateBuilder} 构建 {@code RestTemplate} 时自动挂载（业务侧零改动）：
 * 先把请求工厂包一层 {@link BufferingClientHttpRequestFactory}（保证请求体可被拦截器读取），
 * 再注册 {@link OutboundLogInterceptor} 到拦截器链首。</p>
 *
 * @author BlueWhale
 */
public class OutboundLogRestTemplateCustomizer implements RestTemplateCustomizer {

    private final OutboundLogInterceptor outboundLogInterceptor;

    /**
     * 构造定制器
     *
     * @param outboundLogInterceptor 出站日志拦截器
     */
    public OutboundLogRestTemplateCustomizer(OutboundLogInterceptor outboundLogInterceptor) {
        this.outboundLogInterceptor = outboundLogInterceptor;
    }

    @Override
    public void customize(RestTemplate restTemplate) {
        ClientHttpRequestFactory factory = restTemplate.getRequestFactory();
        if (factory != null) {
            restTemplate.setRequestFactory(new BufferingClientHttpRequestFactory(factory));
        }
        restTemplate.getInterceptors().add(outboundLogInterceptor);
    }
}
