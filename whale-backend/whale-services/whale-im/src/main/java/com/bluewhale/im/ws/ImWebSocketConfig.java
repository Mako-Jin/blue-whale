package com.bluewhale.im.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 端点配置。
 *
 * <p>注册升级路径 {@code /im/ws}（网关 {@code lb:ws://whale-im} 原样转发，不剥离 {@code /im} 前缀），
 * 握手必须经过 {@link ImWsHandshakeInterceptor} 的一次性 ticket 校验。</p>
 *
 * @author BlueWhale
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class ImWebSocketConfig implements WebSocketConfigurer {

    private final ImWebSocketHandler handler;

    private final ImWsHandshakeInterceptor handshakeInterceptor;

    /** 允许的握手来源，逗号分隔，默认全部放行（生产可收紧为同源域名） */
    @Value("${whale.im.ws.allowed-origins:*}")
    private String allowedOrigins;

    /**
     * 注册 WebSocket 端点。
     *
     * @param registry 端点注册表
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/im/ws")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins.split(","));
    }
}
