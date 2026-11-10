package com.bluewhale.im.vo;

import lombok.Builder;
import lombok.Data;

/**
 * WebSocket 一次性凭证视图对象。
 *
 * <p>{@code ticket} 仅能在 {@code /im/ws} 握手时使用一次，有效期默认 60 秒；
 * JWT 严禁出现在 WS URL 中，避免落入网关与代理访问日志。</p>
 *
 * @author BlueWhale
 */
@Data
@Builder
public class WsTicketVO {

    /** 一次性 WS ticket（随机串，非 JWT） */
    private String ticket;

    /** 有效期（秒） */
    private long expiresIn;
}
