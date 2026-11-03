package com.bluewhale.video.domain.repository;

import com.bluewhale.video.domain.model.PlayTicket;

import java.util.Optional;

/**
 * 播放票据仓储端口。
 *
 * @author BlueWhale
 */
public interface PlayTicketRepository {

    /**
     * 签发票据。
     *
     * @param ticket 票据
     * @param ttlSeconds 有效期（秒）
     */
    void issue(PlayTicket ticket, int ttlSeconds);

    /**
     * 按票据令牌读取绑定信息。
     *
     * @param ticket 票据令牌
     * @return 票据；不存在或格式非法返回空
     */
    Optional<PlayTicket> find(String ticket);

    /**
     * 撤销票据。
     *
     * @param ticket 票据令牌
     */
    void revoke(String ticket);
}
