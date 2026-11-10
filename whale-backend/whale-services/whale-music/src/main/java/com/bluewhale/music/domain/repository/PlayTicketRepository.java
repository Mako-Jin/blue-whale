package com.bluewhale.music.domain.repository;

import com.bluewhale.music.domain.model.PlayTicket;

import java.util.Optional;

/**
 * 播放票据仓储端口（M2 由 Redis 实现）。
 *
 * @author BlueWhale
 */
public interface PlayTicketRepository {

    /**
     * 写入票据，带过期时间。
     *
     * @param ticket 票据
     * @param ttlSeconds 有效秒数
     */
    void issue(PlayTicket ticket, int ttlSeconds);

    /**
     * 按令牌读取票据绑定信息。
     *
     * @param ticket 票据令牌
     * @return 票据；不存在或已过期返回空
     */
    Optional<PlayTicket> find(String ticket);

    /**
     * 主动失效票据（版权下架时使用）。
     *
     * @param ticket 票据令牌
     */
    void revoke(String ticket);
}
