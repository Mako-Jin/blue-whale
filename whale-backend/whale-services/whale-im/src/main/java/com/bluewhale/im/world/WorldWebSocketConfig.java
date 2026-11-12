package com.bluewhale.im.world;

import com.bluewhale.im.ws.ImWsHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 世界频道 WebSocket 端点配置（{@code /im/world}）。
 *
 * <p>独立成一个 {@link WebSocketConfigurer}：Spring 会把容器内所有 configurer 依次应用到
 * 同一个端点注册表，因此这里只追加 {@code /im/world}，<b>不改动既有的 {@code /im/ws}</b>——
 * 2D 聊天端点的行为、连接上限、在线状态联动全部保持原样。</p>
 *
 * <p>握手复用 {@link ImWsHandshakeInterceptor}：一次 ticket 只能用一次，
 * 因此 3D 客户端连世界频道前也要先调 {@code POST /im/ws-ticket} 换票。</p>
 *
 * @author BlueWhale
 */
@Configuration
@RequiredArgsConstructor
public class WorldWebSocketConfig implements WebSocketConfigurer {

    private final WorldWebSocketHandler handler;

    private final ImWsHandshakeInterceptor handshakeInterceptor;

    /** 允许的握手来源，逗号分隔，默认全部放行（生产可收紧为同源域名） */
    @Value("${whale.im.world.allowed-origins:*}")
    private String allowedOrigins;

    /**
     * 注册世界频道端点。
     *
     * @param registry 端点注册表
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/im/world")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins.split(","));
    }
}
