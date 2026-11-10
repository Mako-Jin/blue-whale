package com.bluewhale.auth.application.model.port;

import java.util.Date;

/**
 * 用户级访问撤销端口（停用、删除、改密后即时失效存量令牌）。
 *
 * <p>与租户成员关系撤销（按租户维度）的区别：成员解绑按租户维度撤销，
 * 用户状态/口令变更跨租户生效，因此本端口按用户 ID 记录撤销时刻，网关与 auth 服务
 * 两侧共用。存储故障时按「未撤销」降级，避免鉴权链路整体不可用。</p>
 *
 * @author BlueWhale
 */
public interface UserAccessRevocationPort {

    /**
     * 记录用户级访问撤销时间（当前时间）
     *
     * @param userId 用户 ID
     */
    void revoke(Long userId);

    /**
     * 判断令牌是否在用户级撤销前签发
     *
     * @param userId 令牌用户 ID
     * @param issuedAt 令牌签发时间
     * @return 已撤销且令牌签发时间不晚于撤销时间时返回 {@code true}
     */
    boolean isRevoked(Long userId, Date issuedAt);
}
