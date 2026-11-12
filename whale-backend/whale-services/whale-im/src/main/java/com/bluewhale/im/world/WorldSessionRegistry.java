package com.bluewhale.im.world;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 世界频道专用会话注册表。
 *
 * <p><b>为什么不复用聊天用的 {@code WsSessionRegistry}</b>：那个注册表按「单用户 5 连接」
 * 限流并联动在线状态（presence）。3D 场景多开标签页很常见，若共用配额，
 * 玩家开一个城市场景就可能把聊天连接挤掉；反过来场景断开也不该被当成「离线」。
 * 因此世界频道自成一套连接管理，互不影响——这是 3D 与 2D 的隔离边界之一。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorldSessionRegistry {

    /** Jackson 序列化器（Spring 管理的 Jackson 3 实例） */
    private final ObjectMapper objectMapper;

    /** 单用户世界频道最大并行连接数 */
    private static final int MAX_SESSIONS_PER_USER = 3;

    /** 用户ID → 该用户的世界频道连接 */
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    /**
     * 注册连接。
     *
     * @param userId  用户ID
     * @param session WebSocket 会话
     * @return 超过单用户上限返回 {@code false}
     */
    public boolean register(long userId, WebSocketSession session) {
        Set<WebSocketSession> userSessions = sessions.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet());
        synchronized (userSessions) {
            if (userSessions.size() >= MAX_SESSIONS_PER_USER) {
                log.warn("世界频道连接数超过上限，拒绝新连接: userId={}", userId);
                return false;
            }
            return userSessions.add(session);
        }
    }

    /**
     * 摘除连接。
     *
     * @param userId  用户ID
     * @param session WebSocket 会话
     * @return 该用户是否已无世界频道连接
     */
    public boolean unregister(long userId, WebSocketSession session) {
        boolean[] last = {false};
        sessions.computeIfPresent(userId, (key, userSessions) -> {
            userSessions.remove(session);
            if (userSessions.isEmpty()) {
                last[0] = true;
                return null;
            }
            return userSessions;
        });
        return last[0];
    }

    /**
     * 向单个用户发送帧。
     *
     * @param userId 用户ID
     * @param frame  帧对象
     */
    public void send(long userId, Map<String, Object> frame) {
        send(List.of(userId), frame);
    }

    /**
     * 向多个用户发送同一帧（序列化一次）。
     *
     * @param userIds 用户ID集合
     * @param frame   帧对象
     */
    public void send(Collection<Long> userIds, Map<String, Object> frame) {
        if (userIds == null || userIds.isEmpty() || frame == null) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(frame);
        } catch (RuntimeException e) {
            log.warn("世界频道帧序列化失败", e);
            return;
        }
        for (Long userId : userIds) {
            Set<WebSocketSession> userSessions = sessions.get(userId);
            if (userSessions == null) {
                continue;
            }
            for (WebSocketSession session : userSessions) {
                sendToSession(userId, session, payload);
            }
        }
    }

    /**
     * 向单个会话发送文本载荷，失败即摘除。
     *
     * @param userId  用户ID
     * @param session WebSocket 会话
     * @param payload JSON 文本
     */
    private void sendToSession(long userId, WebSocketSession session, String payload) {
        try {
            if (!session.isOpen()) {
                unregister(userId, session);
                return;
            }
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (IOException | RuntimeException e) {
            log.warn("世界频道推送失败，摘除会话: userId={}", userId, e);
            unregister(userId, session);
            closeQuietly(session);
        }
    }

    /**
     * 静默关闭会话。
     *
     * @param session WebSocket 会话
     */
    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException | RuntimeException e) {
            log.debug("世界频道会话关闭失败: sessionId={}", session.getId(), e);
        }
    }
}
