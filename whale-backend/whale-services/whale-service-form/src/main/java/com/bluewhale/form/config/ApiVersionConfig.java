package com.bluewhale.form.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.accept.ApiVersionResolver;
import org.springframework.web.accept.PathApiVersionResolver;

import java.util.regex.Pattern;

/**
 * Spring Boot 4 API Versioning 配置：从路径中 {@code v{n}} 段解析版本号。
 *
 * <p>只对形如 {@code /form/v1/...}、{@code /form/v2/...} 的路径（第 2 段为 v+数字）
 * 尝试解析，避免把 {@code /form/data} 的普通路径段误判为版本号；未命中版本段的
 * 请求返回 {@code null}，由未声明版本的既有接口按原路径继续匹配。</p>
 *
 * <p>配合 {@code @RequestMapping(path = "/form/v{version}", version = "1")} 使用，
 * 例如表单定义接口的实际路径为 {@code /form/v1/definition}。</p>
 *
 * <p>说明：只要服务内出现任何 {@code @RequestMapping(version = ...)}，
 * 就必须存在 {@link ApiVersionResolver} bean，否则
 * {@code RequestMappingHandlerMapping} 初始化失败、容器反复重启。</p>
 *
 * @author BlueWhale
 */
@Configuration(proxyBeanMethods = false)
public class ApiVersionConfig {

    /** 版本路径段：form 前缀之后的 v+数字段，可选后续路径（如 /form/v1/definition） */
    private static final Pattern VERSIONED_PATH = Pattern.compile("^/form/v\\d+(/.*)?$");

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
