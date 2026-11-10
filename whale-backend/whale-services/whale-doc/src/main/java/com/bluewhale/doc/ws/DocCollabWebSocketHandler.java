package com.bluewhale.doc.ws;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.doc.application.service.DocCollabService;
import com.bluewhale.doc.config.DocProperties;
import com.bluewhale.doc.domain.exception.DocErrorCode;
import com.bluewhale.doc.support.collab.DocCollabInit;
import com.bluewhale.doc.support.collab.DocCollabPayloads;
import com.bluewhale.doc.support.collab.DocCollabSnapshot;
import com.bluewhale.doc.support.collab.DocCollabStore;
import com.bluewhale.doc.support.collab.DocPresencePalette;
import com.bluewhale.doc.support.collab.DocPresenceUser;
import com.bluewhale.doc.support.collab.DocWsTicket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * 文档实时协作 WebSocket 处理器（M2）。
 *
 * <p>客户端协议（JSON 文本）：上行 {@code update}/{@code seed}/{@code awareness}/
 * {@code snapshot}/{@code leader-claim}/{@code presence}/{@code ping}；下行
 * {@code ready}/{@code update}/{@code awareness}/{@code presence}/{@code leader}/
 * {@code snapshot-ack}/{@code pong}/{@code error}。服务端只中继 Yjs 二进制（base64），
 * 不解析正文；持久化由 leader 客户端周期提交完整正文快照。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocCollabWebSocketHandler extends TextWebSocketHandler {

    /** 客户端ID最大长度 */
    private static final int MAX_CLIENT_ID_LENGTH = 64;

    /** 在线用户名最大长度 */
    private static final int MAX_PRESENCE_NAME_LENGTH = 64;

    /** 系统错误码 */
    private static final int SYSTEM_ERROR = 500;

    private final DocCollabService collabService;

    private final DocCollabStore collabStore;

    private final DocCollabSessionRegistry registry;

    private final DocCollabFrames frames;

    private final DocProperties docProperties;

    private final ObjectMapper objectMapper;

    /**
     * 连接建立后加载协作状态并发送 ready。
     *
     * @param session WebSocket 会话
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        DocWsTicket ticket = (DocWsTicket) session.getAttributes().get(DocCollabAttributes.TICKET);
        if (ticket == null) {
            closeQuietly(session, CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        String clientId = resolveClientId(session);
        DocCollabSession collabSession = new DocCollabSession(session, ticket, clientId);
        session.getAttributes().put(DocCollabAttributes.SESSION, collabSession);
        TenantContext.set(ticket.tenantId());
        try {
            openSession(collabSession);
        } catch (BizException ex) {
            sendError(collabSession, ex.getCode(), ex.getMessage());
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
        } catch (RuntimeException ex) {
            log.warn("协作连接初始化失败: pageId={}, reason={}", ticket.pageId(), ex.getMessage());
            sendError(collabSession, DocErrorCode.COLLAB_UNAVAILABLE, "实时协作暂不可用");
            closeQuietly(session, CloseStatus.SERVER_ERROR);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * 处理协作 JSON 帧。
     *
     * @param session WebSocket 会话
     * @param message 文本帧
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        DocCollabSession collabSession = currentSession(session);
        if (collabSession == null) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        TenantContext.set(collabSession.tenantId());
        try {
            dispatch(collabSession, message.getPayload());
        } catch (BizException ex) {
            sendError(collabSession, ex.getCode(), ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("协作帧处理失败: pageId={}, reason={}", collabSession.pageId(), ex.getMessage());
            sendError(collabSession, SYSTEM_ERROR, "协作帧处理失败");
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * 连接关闭后清理 presence，并补选 leader。
     *
     * @param session WebSocket 会话
     * @param status  关闭状态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        DocCollabSession collabSession = currentSession(session);
        if (collabSession == null) {
            return;
        }
        registry.unregister(collabSession);
        removePresence(collabSession);
        electLeader(collabSession.tenantId(), collabSession.pageId());
        log.info("协作连接关闭: pageId={}, clientId={}, status={}",
                collabSession.pageId(), collabSession.getClientId(), status);
    }

    /**
     * 处理传输异常并关闭连接。
     *
     * @param session    WebSocket 会话
     * @param exception  异常
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("协作连接传输异常: session={}, reason={}", session.getId(), exception.getMessage());
        closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    /**
     * 初始化连接：加载正文/更新日志、注册会话、抢占 leader 并发送 ready。
     *
     * @param session 协作会话
     */
    private void openSession(DocCollabSession session) {
        DocCollabInit init = collabService.load(session.getTicket());
        DocPresenceUser presence = presenceOf(session);
        session.setPresence(presence);
        registry.register(session);
        collabStore.touchPresence(session.tenantId(), session.pageId(), presence);
        boolean leader = collabStore.claimLeader(session.tenantId(), session.pageId(), session.getClientId());
        session.setLeader(leader);
        List<DocPresenceUser> users = collabStore.presenceUsers(session.tenantId(), session.pageId());
        String frame = frames.ready(init, session.getClientId(), session.getTicket().username(), leader,
                users, collabService.snapshotSeconds());
        registry.send(session, frame);
        broadcastPresence(session, session.getClientId(), null);
        log.info("协作连接建立: pageId={}, clientId={}, leader={}, updates={}",
                init.pageId(), session.getClientId(), leader, init.updates().size());
    }

    /**
     * 按帧类型分发。
     *
     * @param session 协作会话
     * @param payload 帧 JSON
     */
    private void dispatch(DocCollabSession session, String payload) {
        JsonNode node = objectMapper.readTree(payload);
        String type = text(node, "type");
        if (type == null) {
            throw DocErrorCode.exception(DocErrorCode.COLLAB_FRAME_INVALID);
        }
        switch (type) {
            case "update", "seed" -> onUpdate(session, node);
            case "awareness" -> onAwareness(session, node);
            case "snapshot" -> onSnapshot(session, node);
            case "leader-claim" -> onLeaderClaim(session);
            case "presence" -> onPresence(session, node);
            case "ping" -> onPing(session);
            default -> throw DocErrorCode.exception(DocErrorCode.COLLAB_FRAME_INVALID);
        }
    }

    /**
     * 中继 Yjs 更新。
     *
     * @param session 协作会话
     * @param node    帧
     */
    private void onUpdate(DocCollabSession session, JsonNode node) {
        String update = text(node, "update");
        collabService.recordUpdate(session.getTicket(), update);
        touchPresence(session);
        String frame = frames.update(update, session.getClientId());
        broadcastFrame(session, frame, session.getClientId(), null);
    }

    /**
     * 中继 awareness 光标更新。
     *
     * @param session 协作会话
     * @param node    帧
     */
    private void onAwareness(DocCollabSession session, JsonNode node) {
        String update = text(node, "update");
        DocCollabPayloads.decode(update, docProperties.getCollabMaxUpdateBytes());
        String frame = frames.awareness(update, session.getClientId());
        broadcastFrame(session, frame, session.getClientId(), null);
    }

    /**
     * 处理 leader 快照提交。
     *
     * @param session 协作会话
     * @param node    帧
     */
    private void onSnapshot(DocCollabSession session, JsonNode node) {
        if (!collabStore.claimLeader(session.tenantId(), session.pageId(), session.getClientId())) {
            session.setLeader(false);
            registry.send(session, frames.leader(session.getClientId(), false));
            return;
        }
        session.setLeader(true);
        JsonNode contentJson = parseContentJson(node.get("contentJson"));
        DocCollabSnapshot snapshot = collabService.snapshot(session.getTicket(), text(node, "title"), contentJson);
        String frame = frames.snapshotAck(snapshot);
        broadcastFrame(session, frame, null, null);
    }

    /**
     * 处理 leader 抢占。
     *
     * @param session 协作会话
     */
    private void onLeaderClaim(DocCollabSession session) {
        boolean leader = collabStore.claimLeader(session.tenantId(), session.pageId(), session.getClientId());
        session.setLeader(leader);
        if (leader) {
            broadcastLeader(session, true);
        } else {
            registry.send(session, frames.leader(session.getClientId(), false));
        }
    }

    /**
     * 更新在线用户信息并广播。
     *
     * @param session 协作会话
     * @param node    帧
     */
    private void onPresence(DocCollabSession session, JsonNode node) {
        DocPresenceUser presence = session.getPresence();
        if (presence == null) {
            presence = presenceOf(session);
        }
        String name = text(node, "name");
        if (name != null && !name.isBlank() && name.length() <= MAX_PRESENCE_NAME_LENGTH) {
            presence.setName(name.trim());
        }
        String color = text(node, "color");
        if (color != null && color.matches("#[0-9A-Fa-f]{6}")) {
            presence.setColor(color);
        }
        session.setPresence(presence);
        collabStore.touchPresence(session.tenantId(), session.pageId(), presence);
        broadcastPresence(session, null, null);
    }

    /**
     * 处理心跳：刷新 presence、补位 leader 并应答。
     *
     * @param session 协作会话
     */
    private void onPing(DocCollabSession session) {
        touchPresence(session);
        ensureLeadership(session);
        registry.send(session, frames.pong());
    }

    /**
     * 心跳时确保页面存在 leader。
     *
     * @param session 协作会话
     */
    private void ensureLeadership(DocCollabSession session) {
        boolean leader = collabStore.claimLeader(session.tenantId(), session.pageId(), session.getClientId());
        if (leader && !session.isLeader()) {
            session.setLeader(true);
            broadcastLeader(session, true);
        } else if (!leader && session.isLeader()) {
            session.setLeader(false);
            registry.send(session, frames.leader(session.getClientId(), false));
        }
    }

    /**
     * 广播 leader 状态：成为 leader 时通知全页，失去时仅通知本人。
     *
     * @param session 协作会话
     * @param leader  是否 leader
     */
    private void broadcastLeader(DocCollabSession session, boolean leader) {
        String frame = frames.leader(session.getClientId(), leader);
        if (leader) {
            broadcastFrame(session, frame, null, null);
        } else {
            registry.send(session, frame);
            collabStore.publish(session.tenantId(), session.pageId(), frame, null, session.getClientId());
        }
    }

    /**
     * 连接断开后从本实例连接中补选 leader。
     *
     * @param tenantId 租户ID
     * @param pageId   页面ID
     */
    private void electLeader(Long tenantId, Long pageId) {
        List<DocCollabSession> local = registry.localSessions(tenantId, pageId);
        for (DocCollabSession candidate : local) {
            if (collabStore.claimLeader(tenantId, pageId, candidate.getClientId())) {
                candidate.setLeader(true);
                broadcastLeader(candidate, true);
                return;
            }
        }
    }

    /**
     * 广播在线用户。
     *
     * @param session         当前会话
     * @param excludeClientId 排除的客户端ID，可为 {@code null}
     * @param targetClientId  仅投递的客户端ID，可为 {@code null}
     */
    private void broadcastPresence(DocCollabSession session, String excludeClientId, String targetClientId) {
        List<DocPresenceUser> users = collabStore.presenceUsers(session.tenantId(), session.pageId());
        String frame = frames.presence(users);
        registry.broadcastLocal(session.tenantId(), session.pageId(), frame, excludeClientId, targetClientId);
        collabStore.publish(session.tenantId(), session.pageId(), frame, excludeClientId, targetClientId);
    }

    /**
     * 本实例与跨实例同时广播帧。
     *
     * @param session         当前会话
     * @param frame           帧 JSON
     * @param excludeClientId 排除的客户端ID
     * @param targetClientId  仅投递的客户端ID
     */
    private void broadcastFrame(DocCollabSession session, String frame, String excludeClientId, String targetClientId) {
        registry.broadcastLocal(session.tenantId(), session.pageId(), frame, excludeClientId, targetClientId);
        collabStore.publish(session.tenantId(), session.pageId(), frame, excludeClientId, targetClientId);
    }

    /**
     * 刷新当前会话 presence。
     *
     * @param session 协作会话
     */
    private void touchPresence(DocCollabSession session) {
        DocPresenceUser presence = session.getPresence();
        if (presence == null) {
            presence = presenceOf(session);
            session.setPresence(presence);
        }
        collabStore.touchPresence(session.tenantId(), session.pageId(), presence);
    }

    /**
     * 移除离线 presence 并广播最新在线列表。
     *
     * @param session 协作会话
     */
    private void removePresence(DocCollabSession session) {
        collabStore.removePresence(session.tenantId(), session.pageId(), session.getClientId());
        collabStore.releaseLeader(session.tenantId(), session.pageId(), session.getClientId());
        broadcastPresence(session, session.getClientId(), null);
    }

    /**
     * 构造在线用户。
     *
     * @param session 协作会话
     * @return 在线用户
     */
    private DocPresenceUser presenceOf(DocCollabSession session) {
        DocPresenceUser user = new DocPresenceUser();
        user.setClientId(session.getClientId());
        user.setUserId(session.userId());
        user.setName(session.getTicket().username());
        user.setColor(DocPresencePalette.color(session.userId()));
        user.setLastSeen(System.currentTimeMillis());
        return user;
    }

    /**
     * 解析客户端提交的快照正文。
     *
     * @param node 正文节点
     * @return 正文 JSON；缺失返回 {@code null}
     */
    private JsonNode parseContentJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return objectMapper.readTree(node.asText());
        }
        return node;
    }

    /**
     * 从帧取文本字段。
     *
     * @param node  帧
     * @param field 字段名
     * @return 文本；缺失返回 {@code null}
     */
    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /**
     * 解析或生成客户端ID。
     *
     * @param session WebSocket 会话
     * @return 客户端ID
     */
    private String resolveClientId(WebSocketSession session) {
        Object value = session.getAttributes().get(DocCollabAttributes.CLIENT_ID);
        if (value instanceof String clientId && clientId.length() <= MAX_CLIENT_ID_LENGTH) {
            return clientId;
        }
        return UUID.randomUUID().toString();
    }

    /**
     * 从 WebSocket 会话还原协作会话。
     *
     * @param session WebSocket 会话
     * @return 协作会话；不存在返回 {@code null}
     */
    private DocCollabSession currentSession(WebSocketSession session) {
        Object value = session.getAttributes().get(DocCollabAttributes.SESSION);
        return value instanceof DocCollabSession collabSession ? collabSession : null;
    }

    /**
     * 发送错误帧。
     *
     * @param session 协作会话
     * @param code    错误码
     * @param message 提示
     */
    private void sendError(DocCollabSession session, int code, String message) {
        registry.send(session, frames.error(code, message));
    }

    /**
     * 静默关闭连接。
     *
     * @param session WebSocket 会话
     * @param status  关闭状态
     */
    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (IOException ex) {
            log.debug("协作连接关闭失败: session={}, reason={}", session.getId(), ex.getMessage());
        }
    }
}
