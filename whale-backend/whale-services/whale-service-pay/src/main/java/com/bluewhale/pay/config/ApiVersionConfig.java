package com.bluewhale.pay.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.accept.ApiVersionResolver;
import org.springframework.web.accept.PathApiVersionResolver;

import java.util.regex.Pattern;

/**
 * 支付服务 API 版本解析配置：从路径第 2 段的 {@code v{n}} 解析版本号。
 *
 * <p>只对形如 {@code /pay/v1/...}、{@code /pay/v2/...} 的路径尝试解析，
 * 避免把 {@code /pay/order} 等普通路径段误判为版本号；未命中版本段的请求
 * 返回 {@code null}，由未声明版本的接口按原路径匹配。</p>
 *
 * <p>配合 {@code @RequestMapping(path = "/pay/v{version}/...", version = "1")}
 * 使用，例如支付单分页的实际路径为 {@code /pay/v1/order/page}。</p>
 *
 * <p>说明：只要服务内出现任何 {@code @RequestMapping(version = ...)}，
 * 就必须存在 {@link ApiVersionResolver} bean，否则
 * {@code RequestMappingHandlerMapping} 初始化失败、容器反复重启。</p>
 *
 * @author BlueWhale
 */
@Configuration(proxyBeanMethods = false)
public class ApiVersionConfig {

    /** 版本路径段：pay 前缀之后的 v+数字段，可选后续路径（如 /pay/v1/order/page） */
    private static final Pattern VERSIONED_PATH = Pattern.compile("^/pay/v\\d+(/.*)?$");

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
