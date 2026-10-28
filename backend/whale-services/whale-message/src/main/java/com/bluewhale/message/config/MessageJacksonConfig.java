package com.bluewhale.message.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 消息服务 Jackson 2 映射器配置。
 *
 * <p>Spring Boot 4 默认自动装配 Jackson 3（{@code tools.jackson.*}），
 * 而渠道适配器（邮件/短信/IM）沿用 Jackson 2 API 序列化模板变量与请求体。
 * 这里显式注册内部使用的 {@link ObjectMapper} Bean，不参与 Spring MVC 的 HTTP 序列化。</p>
 *
 * @author BlueWhale
 */
@Configuration
public class MessageJacksonConfig {

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
