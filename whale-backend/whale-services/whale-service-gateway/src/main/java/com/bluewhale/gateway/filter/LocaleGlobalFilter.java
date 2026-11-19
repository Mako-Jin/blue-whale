package com.bluewhale.gateway.filter;

import com.bluewhale.common.core.i18n.LocaleSupport;
import com.bluewhale.gateway.common.GatewayHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 语言上下文过滤器：归一化客户端语言并注入 {@code X-Lang}。
 *
 * <p>取值优先级：入站 {@code X-Lang}（仅接受受支持语言）→ {@code Accept-Language} →
 * 默认 {@code zh-CN}。归一化后同时覆盖下游 {@code Accept-Language}，避免各服务自行解析
 * {@code zh_CN} / {@code en} / {@code en-GB} 时口径不一致；并回写 {@code Content-Language}
 * 便于浏览器与排障工具识别实际响应语言。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class LocaleGlobalFilter implements GlobalFilter, Ordered {

    /**
     * 解析语言并透传给下游。
     *
     * @param exchange 当前请求
     * @param chain    过滤器链
     * @return 继续执行的响应流
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String language = LocaleSupport.resolve(
                headers.getFirst(GatewayHeaders.LANGUAGE),
                headers.getFirst(HttpHeaders.ACCEPT_LANGUAGE));
        exchange.getResponse().getHeaders().set(HttpHeaders.CONTENT_LANGUAGE, language);
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(request -> request.headers(target -> {
                    target.set(GatewayHeaders.LANGUAGE, language);
                    target.set(HttpHeaders.ACCEPT_LANGUAGE, language);
                }))
                .build();
        if (log.isDebugEnabled()) {
            log.debug("语言上下文归一化: path={}, language={}",
                    exchange.getRequest().getPath().value(), language);
        }
        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        // 早于追踪（-200）与鉴权（-100）：任何提前返回的错误响应也带正确语言
        return -300;
    }
}
