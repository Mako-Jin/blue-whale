package com.bluewhale.forum.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 通知 outbox 实体，对应表 {@code fm_notify_outbox}。
 *
 * <p><b>先落库、再投递</b>：whale-message 不可达时消息留在 {@code PENDING}，
 * 由补偿任务重投。若直接同步调外部服务，一次抖动就等于永久丢通知。</p>
 *
 * <p>{@code idempotencyKey} 在租户内唯一，保证「同一事件只打扰一次」——
 * 重试、并发处理同一工单都不会重复发信。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fm_notify_outbox")
public class ForumNotifyOutbox extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户ID */
    private Long tenantId;

    /** 接收人用户ID */
    private Long receiverId;

    /** 业务类型：REPLY / MENTION / MODERATION */
    private String bizType;

    /** 业务主键（帖子/楼层/工单ID） */
    private Long bizId;

    /** 幂等键 */
    private String idempotencyKey;

    /** 消息标题 */
    private String title;

    /** 消息正文 */
    private String content;

    /** 状态：PENDING / SENT / FAILED */
    private String status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 最后一次失败原因 */
    private String lastError;

    /** 下次重试时间 */
    private LocalDateTime nextRetryTime;
}
