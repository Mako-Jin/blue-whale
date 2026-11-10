package com.bluewhale.auth.starter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.accept.ApiVersionResolver;
import org.springframework.web.accept.PathApiVersionResolver;

import java.util.regex.Pattern;

/**
 * Spring Boot 4 API Versioning 配置：从路径首段解析版本号。
 *
 * <p>只对形如 {@code /v1/...}、{@code /v2/...} 的路径尝试解析，避免把
 * {@code /user/page} 的首段误判为版本号；未命中版本段的请求返回 {@code null}，
 * 由未声明版本的既有接口按原路径继续匹配。</p>
 *
 * <p>配合 {@code @RequestMapping(path = "/{version}/...", version = "1")} 使用，
 * 例如认证接口的实际路径为 {@code /v1/auth/login}。</p>
 *
 * @author BlueWhale
 */
@Configuration(proxyBeanMethods = false)
public class ApiVersionConfig {

    /** 版本路径段：/v + 数字，可选后续路径 */
    private static final Pattern VERSIONED_PATH = Pattern.compile("^/v\\d+(/.*)?$");

    /**
     * 注册路径段版本解析器
     *
     * @return 仅匹配版本化路径的解析器
     */
    @Bean
    public ApiVersionResolver pathApiVersionResolver() {
        return new PathApiVersionResolver(0,
                requestPath -> VERSIONED_PATH.matcher(requestPath.pathWithinApplication().value()).matches());
    }
}
