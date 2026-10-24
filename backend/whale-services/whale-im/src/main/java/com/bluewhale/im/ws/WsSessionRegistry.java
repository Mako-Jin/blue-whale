package com.bluewhale.im.ws;

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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 在线 WebSocket 会话注册表。
 *
 * <p>以用户ID为维度维护连接集合，支持一个用户多端同时在线；推送时对单个会话加锁，
 * 发送失败或半开连接直接摘除，避免脏会话堆积。一期单实例内存注册；
 * 多实例部署时以 Redis Pub/Sub 广播（规划 §5.3 二期）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsSessionRegistry {

    /** Jackson 序列化器（Spring 管理的 Jackson 3 实例） */
    private final ObjectMapper objectMapper;

    /** 单用户最大并行连接数（多端登录上限） */
    private static final int MAX_SESSIONS_PER_USER = 5;

    /** 用户ID → 该用户的全部在线会话 */
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    /**
     * 注册会话。
     *
     * @param userId  用户ID
     * @param session WebSocket 会话
     * @return 是否注册成功；超过单用户连接上限返回 {@code false}
     */
    public boolean register(Long userId, WebSocketSession session) {
        Set<WebSocketSession> userSessions = sessions.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet());
        synchronized (userSessions) {
            if (userSessions.size() >= MAX_SESSIONS_PER_USER) {
                log.warn("WS 连接数超过上限，拒绝新连接: userId={}", userId);
                return false;
            }
            return userSessions.add(session);
        }
    }

    /**
     * 摘除会话。
     *
     * @param userId  用户ID
     * @param session WebSocket 会话
     * @return 该用户是否已无任何在线会话
     */
    public boolean unregister(Long userId, WebSocketSession session) {
        AtomicBoolean lastSession = new AtomicBoolean(false);
        sessions.computeIfPresent(userId, (key, userSessions) -> {
            userSessions.remove(session);
            if (userSessions.isEmpty()) {
                lastSession.set(true);
                return null;
            }
            return userSessions;
        });
        return lastSession.get();
    }

    /**
     * 向单个用户推送帧。
     *
     * @param userId 用户ID
     * @param frame  帧对象
     */
    public void send(Long userId, Map<String, Object> frame) {
        send(List.of(userId), frame);
    }

    /**
     * 向多个用户推送同一帧（序列化一次）。
     *
     * @param userIds 用户ID集合
     * @param frame   帧对象
     */
    public void send(Collection<Long> userIds, Map<String, Object> frame) {
        String payload = serialize(frame);
        if (payload == null) {
            return;
        }
        for (Long userId : userIds) {
            sendPayload(userId, payload);
        }
    }

    /**
     * 序列化帧对象。
     *
     * @param frame 帧对象
     * @return JSON 文本；序列化失败返回 {@code null}
     */
    private String serialize(Map<String, Object> frame) {
        try {
            return objectMapper.writeValueAsString(frame);
        } catch (RuntimeException e) {
            log.warn("WS 帧序列化失败", e);
            return null;
        }
    }

    /**
     * 向单个用户的全部会话发送已序列化载荷。
     *
     * @param userId  用户ID
     * @param payload JSON 文本
     */
    private void sendPayload(Long userId, String payload) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null || userSessions.isEmpty()) {
            return;
        }
        for (WebSocketSession session : userSessions) {
            sendToSession(userId, session, payload);
        }
    }

    /**
     * 向单个会话发送文本消息；失败时摘除并关闭。
     *
     * @param userId  用户ID
     * @param session WebSocket 会话
     * @param payload JSON 文本
     */
    private void sendToSession(Long userId, WebSocketSession session, String payload) {
        try {
            if (!session.isOpen()) {
                unregister(userId, session);
                return;
            }
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (IOException | RuntimeException e) {
            log.warn("WS 推送失败，摘除会话: userId={}", userId, e);
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
            log.debug("WS 会话关闭失败: sessionId={}", session.getId(), e);
        }
    }
}
