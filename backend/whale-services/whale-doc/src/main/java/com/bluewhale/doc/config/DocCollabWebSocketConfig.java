package com.bluewhale.doc.config;

import com.bluewhale.doc.ws.DocCollabWebSocketHandler;
import com.bluewhale.doc.ws.DocWsHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * 文档协作 WebSocket 端点配置（M2）。
 *
 * <p>端点固定为 {@code /doc/ws}，由网关 {@code lb:ws://whale-doc} 转发；鉴权在握手阶段
 * 消费一次性 ticket。允许来源由 {@code whale.doc.collab-allowed-origins} 在拦截器二次校验。</p>
 *
 * @author BlueWhale
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class DocCollabWebSocketConfig implements WebSocketConfigurer {

    /** 协作 WS 端点路径 */
    private static final String ENDPOINT = "/doc/ws";

    /** 会话空闲超时（毫秒），客户端每 25 秒心跳 */
    private static final long IDLE_TIMEOUT_MILLIS = 5 * 60 * 1000L;

    private final DocCollabWebSocketHandler handler;

    private final DocWsHandshakeInterceptor handshakeInterceptor;

    private final DocProperties docProperties;

    /**
     * 调整容器文本帧缓冲，使其与 {@code collab-max-update-bytes} 对齐。
     *
     * <p>Tomcat 默认仅 8KB，超过后大文档的 Yjs 更新会被拒绝；此处按配置放宽并保持上限一致。</p>
     *
     * @return WS 容器工厂
     */
    @Bean
    public ServletServerContainerFactoryBean docWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        int maxBytes = (int) Math.min(docProperties.getCollabMaxUpdateBytes(), Integer.MAX_VALUE);
        container.setMaxTextMessageBufferSize(maxBytes);
        container.setMaxBinaryMessageBufferSize(maxBytes);
        container.setMaxSessionIdleTimeout(IDLE_TIMEOUT_MILLIS);
        return container;
    }

    /**
     * 注册协作 WS 端点与握手拦截器。
     *
     * @param registry WS 处理器注册表
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, ENDPOINT)
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
