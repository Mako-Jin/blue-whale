package com.bluewhale.alert.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 告警服务内部 Jackson 2 映射器配置。
 *
 * <p>Spring Boot 4 的 Spring MVC 默认使用 Jackson 3（{@code tools.jackson.*}），
 * 本服务通知通道（邮件/短信/Webhook 载荷序列化）沿用 Jackson 2 API。这里显式注册
 * 内部使用的 {@link ObjectMapper} Bean，并用独立名称 {@code alertObjectMapper} 标注，
 * 注入点通过 {@code @Qualifier} 精确引用，避免与自动配置的 Jackson 2 映射器混淆。</p>
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
    @Bean("alertObjectMapper")
    public ObjectMapper alertObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
