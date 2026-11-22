package com.bluewhale.crm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.accept.ApiVersionResolver;
import org.springframework.web.accept.PathApiVersionResolver;

import java.util.regex.Pattern;

/**
 * CRM 服务 API 版本解析配置：从路径第 2 段的 {@code v{n}} 解析版本号。
 *
 * <p>只对形如 {@code /crm/v1/...}、{@code /crm/v2/...} 的路径尝试解析，
 * 未命中版本段的请求返回 {@code null}，由未声明版本的接口按原路径匹配。</p>
 *
 * <p>配合 {@code @RequestMapping(path = "/crm/v{version}/...", version = "1")}
 * 使用，例如客户分页的实际路径为 {@code /crm/v1/customer/page}。</p>
 *
 * @author BlueWhale
 */
@Configuration(proxyBeanMethods = false)
public class ApiVersionConfig {

    /** 版本路径段：crm 前缀之后的 v+数字段，可选后续路径 */
    private static final Pattern VERSIONED_PATH = Pattern.compile("^/crm/v\\d+(/.*)?$");

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
