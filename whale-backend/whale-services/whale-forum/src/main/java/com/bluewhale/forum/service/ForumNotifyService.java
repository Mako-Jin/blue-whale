package com.bluewhale.forum.service;

/**
 * 站内信通知服务（outbox 模式）。
 *
 * <p><b>先落库、再投递</b>：消息先写 {@code fm_notify_outbox} 再尝试发送。
 * 直接同步调 whale-message 的话，一次网络抖动就等于永久丢通知；落了库即使
 * message 不可达，后续补偿任务仍能补发。</p>
 *
 * <p>幂等键在租户内唯一：同一事件（如同一楼层被回复）重复触发只打扰一次。</p>
 *
 * @author BlueWhale
 */
public interface ForumNotifyService {

    /**
     * 落库并尝试立即投递一条站内信。
     *
     * @param receiverId 接收人
     * @param bizType 业务类型：REPLY / MENTION / MODERATION
     * @param bizId 业务主键
     * @param idempotencyKey 幂等键
     * @param title 标题
     * @param content 正文
     */
    void enqueue(Long receiverId, String bizType, Long bizId, String idempotencyKey,
                 String title, String content);

    /**
     * 重投待发消息（补偿任务调用）。
     *
     * @param limit 单批条数
     * @return 本次投递成功条数
     */
    int flushPending(int limit);

    /**
     * 被回复通知。
     *
     * @param threadId 帖子ID
     * @param postId 楼层ID
     * @param threadTitle 帖子标题
     * @param receiverId 楼主/被回复人
     * @param replierName 回复人展示名
     */
    void notifyReply(Long threadId, Long postId, String threadTitle, Long receiverId, String replierName);

    /**
     * 处置通知（删帖/禁言/封号/审核驳回）。
     *
     * @param receiverId 被处置人
     * @param targetType 对象类型
     * @param targetId 对象ID
     * @param action 动作
     * @param reason 原因
     * @param recordId 处置记录ID（用于幂等）
     */
    void notifyModeration(Long receiverId, String targetType, Long targetId, String action,
                          String reason, Long recordId);
}
