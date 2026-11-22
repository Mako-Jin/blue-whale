package com.bluewhale.contract.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.accept.ApiVersionResolver;
import org.springframework.web.accept.PathApiVersionResolver;

import java.util.regex.Pattern;

/**
 * 合同服务 API 版本解析配置：从路径第 2 段的 {@code v{n}} 解析版本号。
 *
 * <p>只对形如 {@code /contract/v1/...}、{@code /contract/v2/...} 的路径尝试解析，
 * 未命中版本段的请求返回 {@code null}；电子签回调 {@code /contract/sign/callback/**}
 * 是对外部登记的回调地址，保持无版本。</p>
 *
 * @author BlueWhale
 */
@Configuration(proxyBeanMethods = false)
public class ApiVersionConfig {

    /** 版本路径段：contract 前缀之后的 v+数字段，可选后续路径 */
    private static final Pattern VERSIONED_PATH = Pattern.compile("^/contract/v\\d+(/.*)?$");

    /**
     * 注册路径段版本解析器。
     *
     * @return 仅匹配版本化路径的解析器（版本段固定为第 2 段）
     */
    @Bean
    public ApiVersionResolver pathApiVersionResolver() {
        return new PathApiVersionResolver(1,
                requestPath -> VERSIONED_PATH.matcher(requestPath.pathWithinApplication().value()).matches());
    }
}
