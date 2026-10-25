package com.bluewhale.archive.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 档案服务 Jackson 2 映射器配置。
 *
 * <p>Spring Boot 4 默认自动装配的是 Jackson 3（{@code tools.jackson.*}），
 * 而内网调用结果解析与快照序列化沿用 Jackson 2 API，
 * 因此在此显式注册一个 {@link ObjectMapper} Bean，供
 * {@code FileArchiveClient} / {@code WorkflowArchiveClient} / {@code FormArchiveClient}
 * 与 {@code ArchiveSnapshotBuilder} 注入使用。该映射器只用于内部 {@code Map} 与对象的互转，
 * 不参与 Spring MVC 的 HTTP 序列化。</p>
 *
 * @author BlueWhale
 */
@Configuration
public class ArchiveJacksonConfig {

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
