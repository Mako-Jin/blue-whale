package com.bluewhale.doc.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本实例文档协作 WS 会话注册表（M2）。
 *
 * <p>按「租户:页面」分组，负责本实例连接的消息投递；跨实例投递由 Redis Pub/Sub 补齐。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class DocCollabSessionRegistry {

    /** 租户:页面 → (clientId → 会话) */
    private final Map<String, Map<String, DocCollabSession>> sessions = new ConcurrentHashMap<>();

    /**
     * 注册新连接。
     *
     * @param session 协作会话
     */
    public void register(DocCollabSession session) {
        sessions.computeIfAbsent(key(session.tenantId(), session.pageId()), k -> new ConcurrentHashMap<>())
                .put(session.getClientId(), session);
    }

    /**
     * 注销连接。
     *
     * @param session 协作会话
     */
    public void unregister(DocCollabSession session) {
        String key = key(session.tenantId(), session.pageId());
        Map<String, DocCollabSession> group = sessions.get(key);
        if (group == null) {
            return;
        }
        group.remove(session.getClientId());
        if (group.isEmpty()) {
            sessions.remove(key);
        }
    }

    /**
     * 查询本实例某页面的全部会话。
     *
     * @param tenantId 租户ID
     * @param pageId   页面ID
     * @return 会话列表（副本）
     */
    public List<DocCollabSession> localSessions(Long tenantId, Long pageId) {
        Map<String, DocCollabSession> group = sessions.get(key(tenantId, pageId));
        return group == null ? List.of() : new ArrayList<>(group.values());
    }

    /**
     * 本实例如某页面的连接数。
     *
     * @param tenantId 租户ID
     * @param pageId   页面ID
     * @return 连接数
     */
    public int localCount(Long tenantId, Long pageId) {
        Map<String, DocCollabSession> group = sessions.get(key(tenantId, pageId));
        return group == null ? 0 : group.size();
    }

    /**
     * 向本实例连接投递帧。
     *
     * @param tenantId        租户ID
     * @param pageId          页面ID
     * @param frame           帧 JSON
     * @param excludeClientId 排除的客户端ID，可为 {@code null}
     * @param targetClientId  仅投递的客户端ID，可为 {@code null} 表示广播
     */
    public void broadcastLocal(Long tenantId, Long pageId, String frame,
                               String excludeClientId, String targetClientId) {
        for (DocCollabSession session : localSessions(tenantId, pageId)) {
            String clientId = session.getClientId();
            if (excludeClientId != null && excludeClientId.equals(clientId)) {
                continue;
            }
            if (targetClientId != null && !targetClientId.equals(clientId)) {
                continue;
            }
            send(session, frame);
        }
    }

    /**
     * 向单个连接投递帧。
     *
     * @param session 协作会话
     * @param frame   帧 JSON
     */
    public void send(DocCollabSession session, String frame) {
        WebSocketSession ws = session.getSession();
        if (ws == null || !ws.isOpen()) {
            return;
        }
        try {
            synchronized (ws) {
                ws.sendMessage(new TextMessage(frame));
            }
        } catch (IOException ex) {
            log.warn("协作风帧发送失败: session={}, reason={}", ws.getId(), ex.getMessage());
        }
    }

    /**
     * 构造分组键。
     *
     * @param tenantId 租户ID
     * @param pageId   页面ID
     * @return 分组键
     */
    private String key(Long tenantId, Long pageId) {
        return tenantId + ":" + pageId;
    }
}
