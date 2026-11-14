package com.bluewhale.file.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.accept.ApiVersionResolver;
import org.springframework.web.accept.PathApiVersionResolver;

import java.util.regex.Pattern;

/**
 * Spring Boot 4 API Versioning 配置：从路径中 {@code v{n}} 段解析版本号。
 *
 * <p>whale-file 的接口契约（{@code FileApi}、{@code FileChunkUploadApi}）使用
 * {@code @RequestMapping(path = "/file/v{version}", version = "1")}；Spring 6.2/7
 * 在有版本映射但容器内没有 {@link org.springframework.web.accept.ApiVersionStrategy}
 * 时会让 {@code RequestMappingHandlerMapping} 初始化失败，整个服务起不来——
 * 必须有本配置注册解析器（与 auth 服务 {@code ApiVersionConfig} 同口径）。</p>
 *
 * <p>只对形如 {@code /file/v1/...} 的路径（第 2 段为 v+数字）尝试解析，未命中版本段的
 * 请求返回 {@code null}，由未声明版本的既有接口按原路径继续匹配。</p>
 *
 * @author BlueWhale
 */
@Configuration(proxyBeanMethods = false)
public class ApiVersionConfig {

    /** 版本路径段：资源前缀之后的 v+数字段，可选后续路径（如 /file/v1/upload/chunk） */
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
