package com.bluewhale.doc.support.collab;

import java.io.Serializable;

/**
 * 文档协作 WS 一次性凭证（M2）。
 *
 * <p>由 {@code POST /doc/ws-ticket} 签发，Redis 保存 60 秒，握手时原子消费并绑定
 * 租户、用户与页面，避免在 WS URL 中暴露 JWT。</p>
 *
 * @param tenantId 租户ID
 * @param userId   用户ID
 * @param pageId   页面ID
 * @param username 用户名（用于在线头像展示）
 * @author BlueWhale
 */
public record DocWsTicket(Long tenantId, Long userId, Long pageId, String username) implements Serializable {
}
