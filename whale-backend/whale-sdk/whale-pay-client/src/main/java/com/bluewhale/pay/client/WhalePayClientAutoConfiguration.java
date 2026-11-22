package com.bluewhale.pay.client;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * 支付客户端自动装配。
 *
 * <p>接入方只需配置 {@code whale.pay.client.base-url} 等属性并注入 {@link WhalePayClient}；
 * 令牌、租户、链路头与重试全部由 SDK 统一处理。可用 {@code whale.pay.client.enabled=false} 关闭。</p>
 *
 * @author BlueWhale
 */
@AutoConfiguration
@ConditionalOnClass(RestClient.class)
@ConditionalOnProperty(prefix = "whale.pay.client", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(WhalePayClientProperties.class)
public class WhalePayClientAutoConfiguration {

    /**
     * 支付客户端
     *
     * @param properties 客户端配置
     * @param builders   宿主 RestClient.Builder（存在时沿用其消息转换器）
     * @return 客户端实现
     */
    @Bean
    @ConditionalOnMissingBean
    public WhalePayClient whalePayClient(WhalePayClientProperties properties,
                                         ObjectProvider<RestClient.Builder> builders) {
        return new HttpWhalePayClient(properties, builders.getIfAvailable(RestClient::builder));
    }
}
