package com.bluewhale.common.internaltoken;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * 出站内网调用令牌拦截器（V-02 纵深防御）。
 *
 * <p>挂载在调用其他服务 {@code /internal/**} 端点的 RestClient 上，为请求自动附加
 * {@code X-Internal-Token} 头；服务端 {@link InternalTokenFilter} 据此校验。
 * 按服务签发模式下头值为 {@code <服务ID>.<密钥>}（本服务身份），否则回退共享令牌；
 * 令牌未配置时不附加（对应服务端 fail-open/dev 场景，或对端未启用校验）。</p>
 *
 * <p>业务侧通过 {@link InternalTokenSupport} 构造 RestClient 时自动挂载，无需手工注册。</p>
 *
 * @author BlueWhale
 */
public class InternalTokenInterceptor implements ClientHttpRequestInterceptor {

    private final InternalTokenProperties properties;

    /**
     * 构造令牌拦截器
     *
     * @param properties 内网令牌配置
     */
    public InternalTokenInterceptor(InternalTokenProperties properties) {
        this.properties = properties;
    }

    /**
     * 为出站请求附加内网令牌头（令牌已配置时）
     *
     * @param request   当前请求
     * @param body      请求体
     * @param execution 执行链
     * @return 下游响应
     * @throws IOException 请求执行异常
     */
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        if (properties.isEnabled()) {
            String token = properties.resolveOutboundToken();
            if (!token.isBlank()) {
                HttpHeaders headers = request.getHeaders();
                if (headers.getFirst(properties.getHeader()) == null) {
                    headers.set(properties.getHeader(), token);
                }
            }
        }
        return execution.execute(request, body);
    }
}
