package com.bluewhale.doc.ws;

import com.bluewhale.doc.support.collab.DocPresenceUser;
import com.bluewhale.doc.support.collab.DocWsTicket;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.web.socket.WebSocketSession;

/**
 * 一个文档协作 WS 连接（M2）。
 *
 * @author BlueWhale
 */
@Getter
@RequiredArgsConstructor
public class DocCollabSession {

    /** WebSocket 会话 */
    private final WebSocketSession session;

    /** 握手消费的票据 */
    private final DocWsTicket ticket;

    /** 浏览器客户端ID */
    private final String clientId;

    /** 是否当前页面 leader（领袖客户端负责周期快照） */
    @Setter
    private volatile boolean leader;

    /** 在线用户信息（心跳刷新 lastSeen） */
    @Setter
    private volatile DocPresenceUser presence;

    /**
     * 租户ID。
     *
     * @return 租户ID
     */
    public Long tenantId() {
        return ticket.tenantId();
    }

    /**
     * 页面ID。
     *
     * @return 页面ID
     */
    public Long pageId() {
        return ticket.pageId();
    }

    /**
     * 用户ID。
     *
     * @return 用户ID
     */
    public Long userId() {
        return ticket.userId();
    }
}
