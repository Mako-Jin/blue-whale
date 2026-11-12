package com.bluewhale.im.world;

import com.bluewhale.im.ws.ImWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * 世界频道 WebSocket 处理器（{@code /im/world}）。
 *
 * <p>与聊天处理器 {@code ImWebSocketHandler} 平行存在：端点不同、会话注册表不同、
 * 帧命名空间不同（{@code world.*}）。握手仍复用一次性 ticket 拦截器，
 * 因此浏览器不在 URL 上携带 JWT。</p>
 *
 * <p>服务端只做位置转发，不落库、不进审计；校验与裁剪逻辑全部在
 * {@link WorldChannelService}，本类只负责「解析 → 调用 → 交给注册表发送」。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class WorldWebSocketHandler extends TextWebSocketHandler {

    /** 单帧最大长度（字节）：位置帧只有几十字节，超长一律拒绝，防内存放大 */
    private static final int MAX_PAYLOAD_BYTES = 2048;

    private final WorldChannelService channelService;

    private final WorldSessionRegistry registry;

    private final ObjectMapper objectMapper;

    /**
     * 构造处理器。
     *
     * @param channelService 世界频道服务
     * @param registry       世界频道会话注册表
     * @param objectMapper   Jackson 序列化器
     */
    public WorldWebSocketHandler(WorldChannelService channelService, WorldSessionRegistry registry,
                                 ObjectMapper objectMapper) {
        this.channelService = channelService;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    /**
     * 连接建立：登记连接，等待客户端发 {@code world.join}。
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
        if (!registry.register(userId, session)) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        log.info("世界频道已连接: userId={}, sessionId={}", userId, session.getId());
    }

    /**
     * 帧处理：join / frame / ping。
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
        String payload = message.getPayload();
        if (payload != null && payload.length() > MAX_PAYLOAD_BYTES) {
            registry.send(userId, WorldFrames.error(WorldFrames.CODE_RATE_LIMITED, "帧过大"));
            closeQuietly(session, CloseStatus.TOO_BIG_TO_PROCESS);
            return;
        }
        JsonNode node = parse(payload);
        if (node == null) {
            registry.send(userId, WorldFrames.error(WorldFrames.CODE_BAD_FRAME, "帧解析失败"));
            return;
        }
        String type = text(node, "type");
        if (type == null) {
            registry.send(userId, WorldFrames.error(WorldFrames.CODE_BAD_FRAME, "缺少 type"));
            return;
        }
        String sceneKey = text(node, "sceneKey");
        switch (type) {
            case WorldFrames.TYPE_JOIN -> handleJoin(userId, node, sceneKey);
            case WorldFrames.TYPE_FRAME -> handleFrame(userId, node, sceneKey);
            case "ping" -> handlePing(userId, sceneKey);
            default -> registry.send(userId, WorldFrames.error(WorldFrames.CODE_BAD_FRAME, "不支持的帧类型: " + type));
        }
    }

    /**
     * 处理进场景。
     *
     * @param userId   用户ID
     * @param node     帧
     * @param sceneKey 场景标识
     */
    private void handleJoin(long userId, JsonNode node, String sceneKey) {
        String key = sceneKey == null ? "" : sceneKey;
        WorldChannelService.JoinOutcome outcome = channelService.join(
                key,
                userId,
                text(node, "name"),
                text(node, "robe"),
                number(node, "x", 0D),
                number(node, "z", 0D),
                number(node, "yaw", 0D));
        if (!outcome.ok()) {
            registry.send(userId, outcome.error());
            return;
        }
        registry.send(userId, outcome.snapshot());
        WorldChannelService.Broadcast enter = outcome.broadcast();
        if (enter != null) {
            registry.send(enter.recipients(), enter.frame());
        }
        broadcastStale(key);
    }

    /**
     * 处理位置帧。
     *
     * @param userId   用户ID
     * @param node     帧
     * @param sceneKey 场景标识
     */
    private void handleFrame(long userId, JsonNode node, String sceneKey) {
        String key = sceneKey == null ? "" : sceneKey;
        WorldChannelService.UpdateOutcome outcome = channelService.update(
                key,
                userId,
                number(node, "x", Double.NaN),
                number(node, "z", Double.NaN),
                number(node, "yaw", 0D),
                number(node, "speed", 0D));
        if (outcome.error() != null) {
            registry.send(userId, outcome.error());
            return;
        }
        if (outcome.hasBroadcast()) {
            WorldChannelService.Broadcast cast = outcome.broadcast();
            registry.send(cast.recipients(), cast.frame());
        }
    }

    /**
     * 心跳：直接回 pong，不触碰场景状态。
     *
     * @param userId   用户ID
     * @param sceneKey 场景标识
     */
    private void handlePing(long userId, String sceneKey) {
        String key = sceneKey == null ? "" : sceneKey;
        if (!key.isEmpty()) {
            broadcastStale(key);
        }
        registry.send(userId, WorldFrames.pong());
    }

    /**
     * 顺带清理超时玩家（每 5 秒最多一次，由服务内部节流）。
     *
     * @param sceneKey 场景标识
     */
    private void broadcastStale(String sceneKey) {
        if (sceneKey.isEmpty()) {
            return;
        }
        WorldChannelService.Broadcast stale = channelService.sweepStale(sceneKey);
        if (stale != null) {
            registry.send(stale.recipients(), stale.frame());
        }
    }

    /**
     * 连接关闭：退场并通知兴趣区内其他人。
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
        registry.unregister(userId, session);
        for (String sceneKey : channelService.sceneKeysOf(userId)) {
            WorldChannelService.Broadcast leave = channelService.leave(sceneKey, userId);
            if (leave != null) {
                registry.send(leave.recipients(), leave.frame());
            }
        }
        log.info("世界频道已断开: userId={}, status={}", userId, status);
    }

    /**
     * 传输异常：记录并关闭（清理依赖关闭回调）。
     *
     * @param session   WebSocket 会话
     * @param exception 异常
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("世界频道传输异常: sessionId={}", session.getId(), exception);
        closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    /**
     * 读取握手阶段写入的用户ID（与聊天端点共用同一个会话属性键）。
     *
     * @param session WebSocket 会话
     * @return 用户ID；未认证返回 {@code null}
     */
    private Long userId(WebSocketSession session) {
        Object value = session.getAttributes().get(ImWebSocketHandler.ATTR_USER_ID);
        return value instanceof Long userId ? userId : null;
    }

    /**
     * 解析 JSON 帧。
     *
     * @param payload 帧文本
     * @return JSON 节点；解析失败返回 {@code null}
     */
    private JsonNode parse(String payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readTree(payload);
        } catch (RuntimeException e) {
            log.debug("世界频道帧解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 读取字符串字段。
     *
     * @param node 帧
     * @param key  字段名
     * @return 文本；缺失返回 {@code null}
     */
    private String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            return null;
        }
        String result = value.asText();
        return result == null || result.isEmpty() ? null : result;
    }

    /**
     * 读取数值字段。
     *
     * @param node     帧
     * @param key      字段名
     * @param fallback 兜底值
     * @return 数值
     */
    private double number(JsonNode node, String key, double fallback) {
        JsonNode value = node.get(key);
        if (value == null || !value.isNumber()) {
            return fallback;
        }
        return WorldChannelService.numberOr(value.numberValue(), fallback);
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
            log.debug("世界频道会话关闭失败: sessionId={}", session.getId(), e);
        }
    }
}
