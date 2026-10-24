package com.bluewhale.im.ws;

import com.bluewhale.im.service.PresenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * WebIM WebSocket 帧处理器。
 *
 * <p>生命周期：握手成功后注册会话、刷新在线状态并下发 {@code ready} 首帧；
 * 收到 {@code ping} 帧续期在线状态并回 {@code pong}；连接断开时摘除会话，
 * 该用户最后一个连接断开即清除在线状态。</p>
 *
 * <p>本波只处理心跳与协议错误，消息发送仍走 REST，服务端在事务提交后主动推送
 * {@code message.new}；机器人流式帧由 M2 接入。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class ImWebSocketHandler extends TextWebSocketHandler {

    /** 会话属性键：握手阶段写入的认证用户ID */
    public static final String ATTR_USER_ID = "whale.im.ws.userId";

    private final WsSessionRegistry registry;

    private final PresenceService presenceService;

    private final ObjectMapper objectMapper;

    private final long heartbeatSeconds;

    /**
     * 构造 WebSocket 处理器。
     *
     * @param registry        会话注册表
     * @param presenceService 在线状态服务
     * @param objectMapper    Jackson 序列化器
     * @param heartbeatSeconds 建议心跳间隔（秒）
     */
    public ImWebSocketHandler(WsSessionRegistry registry, PresenceService presenceService,
                              ObjectMapper objectMapper,
                              @Value("${whale.im.ws.heartbeat-seconds:30}") long heartbeatSeconds) {
        this.registry = registry;
        this.presenceService = presenceService;
        this.objectMapper = objectMapper;
        this.heartbeatSeconds = heartbeatSeconds;
    }

    /**
     * 连接建立：注册会话、标记在线并下发就绪帧。
     *
     * @param session WebSocket 会话
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = userId(session);
        if (userId == null) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        registry.register(userId, session);
        presenceService.touch(userId);
        registry.send(userId, WsFrames.ready(userId, heartbeatSeconds));
        log.info("WS 已连接: userId={}, sessionId={}", userId, session.getId());
    }

    /**
     * 文本帧处理：仅支持 {@code ping} 心跳，其余类型返回协议错误帧。
     *
     * @param session WebSocket 会话
     * @param message 文本帧
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long userId = userId(session);
        if (userId == null) {
            return;
        }
        presenceService.touch(userId);
        String type = parseType(message.getPayload());
        if (WsFrames.TYPE_PING.equals(type)) {
            registry.send(userId, WsFrames.pong());
            return;
        }
        log.warn("忽略未知 WS 帧: userId={}, type={}", userId, type);
        registry.send(userId, WsFrames.error("不支持的帧类型"));
    }

    /**
     * 连接关闭：摘除会话，最后一个连接断开时清除在线状态。
     *
     * @param session WebSocket 会话
     * @param status  关闭状态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = userId(session);
        if (userId == null) {
            return;
        }
        if (registry.unregister(userId, session)) {
            presenceService.offline(userId);
        }
        log.info("WS 已断开: userId={}, status={}", userId, status);
    }

    /**
     * 传输异常：记录日志并关闭会话（清理依赖关闭回调）。
     *
     * @param session   WebSocket 会话
     * @param exception 异常
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WS 传输异常: sessionId={}", session.getId(), exception);
        closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    /**
     * 读取握手阶段写入的用户ID。
     *
     * @param session WebSocket 会话
     * @return 用户ID；未认证返回 {@code null}
     */
    private Long userId(WebSocketSession session) {
        Object value = session.getAttributes().get(ATTR_USER_ID);
        return value instanceof Long userId ? userId : null;
    }

    /**
     * 解析帧类型。
     *
     * @param payload 帧文本
     * @return 帧类型；解析失败或无 type 字段返回 {@code null}
     */
    private String parseType(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            JsonNode type = node.get("type");
            return type == null ? null : type.asText();
        } catch (RuntimeException e) {
            log.warn("WS 帧解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 静默关闭会话。
     *
     * @param session WebSocket 会话
     * @param status  关闭状态
     */
    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException | RuntimeException e) {
            log.debug("WS 会话关闭失败: sessionId={}", session.getId(), e);
        }
    }
}
