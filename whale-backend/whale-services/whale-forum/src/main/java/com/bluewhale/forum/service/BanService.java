package com.bluewhale.forum.service;

import com.bluewhale.forum.entity.ForumUserBan;

/**
 * 禁言/封号服务。
 *
 * <p>判定只认 {@code fm_user_ban} 里 {@code ACTIVE} 且未到期的记录，
 * 不读处置轨迹表——轨迹是历史，判定要的是「此刻是否生效」。</p>
 *
 * @author BlueWhale
 */
public interface BanService {

    /**
     * 查询用户当前生效的处置。
     *
     * @param userId 用户ID
     * @return 生效记录；无则 {@code null}
     */
    ForumUserBan activeBan(Long userId);

    /**
     * 校验用户未被禁言/封号，否则抛 30041。
     *
     * @param userId 用户ID
     */
    void assertNotMuted(Long userId);

    /**
     * 写入一条禁言/封号记录（先解除旧记录，保证同一时刻只有一条生效）。
     *
     * @param userId 用户ID
     * @param banType MUTE / BAN
     * @param days 天数（≤0 表示永久）
     * @param reason 原因（必填）
     * @param operatorId 操作人
     * @param reportId 来源工单ID（可空）
     * @return 新记录
     */
    ForumUserBan ban(Long userId, String banType, int days, String reason,
                     Long operatorId, Long reportId);

    /**
     * 解除用户全部生效处置。
     *
     * @param userId 用户ID
     * @param operatorId 操作人
     * @return 解除条数
     */
    int release(Long userId, Long operatorId);
}
