package com.bluewhale.auth.starter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.accept.ApiVersionResolver;
import org.springframework.web.accept.PathApiVersionResolver;

import java.util.regex.Pattern;

/**
 * Spring Boot 4 API Versioning 配置：从路径中 {@code v{n}} 段解析版本号。
 *
 * <p>只对形如 {@code /auth/v1/...}、{@code /auth/v2/...} 的路径（第 2 段为 v+数字）
 * 尝试解析，避免把 {@code /user/page} 的普通路径段误判为版本号；未命中版本段的
 * 请求返回 {@code null}，由未声明版本的既有接口按原路径继续匹配。</p>
 *
 * <p>配合 {@code @RequestMapping(path = "/auth/v{version}", version = "1")} 使用，
 * 例如认证接口的实际路径为 {@code /auth/v1/login}。</p>
 *
 * @author BlueWhale
 */
@Configuration(proxyBeanMethods = false)
public class ApiVersionConfig {

    /** 版本路径段：资源前缀之后的 v+数字段，可选后续路径（如 /auth/v1/login） */
    private static final Pattern VERSIONED_PATH = Pattern.compile("^/\\w+/v\\d+(/.*)?$");

    /**
     * 注册路径段版本解析器
     *
     * @return 仅匹配版本化路径的解析器（版本段固定为第 2 段）
     */
    @Bean
    public ApiVersionResolver pathApiVersionResolver() {
        return new PathApiVersionResolver(1,
                requestPath -> VERSIONED_PATH.matcher(requestPath.pathWithinApplication().value()).matches());
    }
}
