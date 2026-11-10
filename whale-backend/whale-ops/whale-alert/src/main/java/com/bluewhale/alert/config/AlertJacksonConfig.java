package com.bluewhale.alert.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 告警服务 Jackson 2 映射器配置。
 *
 * <p>Spring Boot 4 默认自动装配 Jackson 3（{@code tools.jackson.*}），而本服务的
 * 通知通道（邮件/短信/Webhook 载荷序列化）沿用 Jackson 2 API。这里显式注册内部
 * 使用的 {@link ObjectMapper} Bean，不参与 Spring MVC 的 HTTP 序列化。</p>
 *
 * @author BlueWhale
 */
@Configuration
public class AlertJacksonConfig {

    /**
     * 注册内部使用的 Jackson 2 JSON 映射器。
     *
     * @return JSON 映射器实例
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
