package com.bluewhale.music.domain.repository;

/**
 * 评论通知端口（M4 评论回复 / 审核结果旁路通知）。
 *
 * <p>通知为尽力而为的旁路能力：实现方必须保证任何失败仅记录告警，
 * 不向调用方抛出异常，不影响评论主事务。</p>
 *
 * @author BlueWhale
 */
public interface CommentNotificationPort {

    /** 评论回复通知业务类型 */
    String BIZ_TYPE_REPLY = "music-comment-reply";

    /** 评论审核结果通知业务类型 */
    String BIZ_TYPE_MODERATION = "music-comment-moderation";

    /**
     * 发送评论相关通知（尽力而为，失败仅告警）。
     *
     * @param tenantId 租户 ID
     * @param bizType 业务类型
     * @param bizId 业务标识（用于幂等去重）
     * @param title 通知标题
     * @param content 通知正文（不含评论正文等敏感内容）
     * @param receiverId 接收人 ID；为 {@code null} 时实现方应直接跳过
     */
    void sendSafely(Long tenantId, String bizType, String bizId, String title, String content,
                    Long receiverId);
}
