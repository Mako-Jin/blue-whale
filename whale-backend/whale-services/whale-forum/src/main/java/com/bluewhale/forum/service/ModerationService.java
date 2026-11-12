package com.bluewhale.forum.service;

import com.bluewhale.forum.common.ForumActor;

/**
 * 处置服务：删帖、删楼层、禁言、解禁。
 *
 * <p>所有处置都<b>必须</b>落 {@code fm_moderation_record}（append-only）并给被处置人发通知，
 * 这是申诉与合规追溯的唯一依据——只改状态不留痕，等于没有治理。</p>
 *
 * @author BlueWhale
 */
public interface ModerationService {

    /**
     * 删除帖子（软删 + 改写编号 + 留痕 + 通知作者）。
     *
     * @param threadId 帖子ID
     * @param reason 原因（必填）
     * @param actor 操作者
     */
    void removeThread(Long threadId, String reason, ForumActor actor);

    /**
     * 删除楼层（软删 + 回复数递减 + 留痕 + 通知作者）。
     *
     * @param postId 楼层ID
     * @param reason 原因（必填）
     * @param actor 操作者
     */
    void removePost(Long postId, String reason, ForumActor actor);

    /**
     * 禁言/封号。
     *
     * @param userId 用户ID
     * @param banType MUTE / BAN
     * @param days 天数（≤0 永久）
     * @param reason 原因（必填）
     * @param actor 操作者
     * @param reportId 来源工单（可空）
     */
    void ban(Long userId, String banType, int days, String reason, ForumActor actor, Long reportId);

    /**
     * 解除禁言/封号。
     *
     * @param userId 用户ID
     * @param actor 操作者
     * @return 解除条数
     */
    int release(Long userId, ForumActor actor);

    /**
     * 写一条处置轨迹（供审核通过/驳回复用）。
     *
     * @param targetType 对象类型
     * @param targetId 对象ID
     * @param action 动作
     * @param reason 原因
     * @param operatorId 操作人
     * @return 轨迹ID
     */
    Long record(String targetType, Long targetId, String action, String reason, Long operatorId);
}
