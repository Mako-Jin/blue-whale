package com.bluewhale.common.internaltoken;

import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

/**
 * 内网调用 RestClient 工厂（V-02 纵深防御）。
 *
 * <p>统一为「调用其他服务 {@code /internal/**} 端点」的出站 RestClient 自动附加
 * 内网令牌头，业务侧用 {@code InternalTokenSupport.restClient()} /
 * {@code InternalTokenSupport.builder()} 替换 {@code RestClient.create()} /
 * {@code RestClient.builder()} 即可，无需注入任何 Bean。</p>
 *
 * <p>静态配置由 {@link com.bluewhale.common.internaltoken.config.InternalTokenAutoConfiguration}
 * 在启动时以 Spring 绑定的 {@link InternalTokenProperties} 初始化（属性绑定优先）；
 * 未初始化时回退读取环境变量 {@code WHALE_INTERNAL_TOKEN}，保证纯单测/工具场景可用。</p>
 *
 * @author BlueWhale
 */
public final class InternalTokenSupport {

    /** 静态共享配置：由自动配置在启动时初始化 */
    private static volatile InternalTokenProperties staticProperties;

    /**
     * 工具类禁止实例化
     */
    private InternalTokenSupport() {
    }

    /**
     * 用 Spring 绑定配置初始化静态共享配置（由自动配置调用，幂等）
     *
     * @param properties Spring 绑定的内网令牌配置
     */
    public static void init(InternalTokenProperties properties) {
        staticProperties = properties;
    }

    /**
     * 构造带内网令牌头的 RestClient（等价于 {@code RestClient.create()} 的安全替换）
     *
     * @return 已挂载令牌拦截器的 RestClient
     */
    public static RestClient restClient() {
        return builder().build();
    }

    /**
     * 构造带内网令牌头的 RestClient.Builder（等价于 {@code RestClient.builder()} 的安全替换）
     *
     * @return 已挂载令牌拦截器的 Builder，可继续链式定制
     */
    public static RestClient.Builder builder() {
        return RestClient.builder().requestInterceptor(interceptor());
    }

    /**
     * 构造内网令牌出站拦截器
     *
     * @return 拦截器
     */
    public static ClientHttpRequestInterceptor interceptor() {
        return new InternalTokenInterceptor(currentProperties());
    }

    /**
     * 取当前生效配置：Spring 绑定配置优先，未初始化时回退默认配置（环境变量解析）
     *
     * @return 生效配置
     */
    private static InternalTokenProperties currentProperties() {
        InternalTokenProperties properties = staticProperties;
        return properties == null ? new InternalTokenProperties() : properties;
    }
}
