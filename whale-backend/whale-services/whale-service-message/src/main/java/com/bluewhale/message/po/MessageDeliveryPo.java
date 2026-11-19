package com.bluewhale.message.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 单渠道投递记录实体，对应表 {@code msg_delivery}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("msg_delivery")
public class MessageDelivery extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 消息ID（逻辑关联 msg_record.id） */
    private Long messageId;

    /** 渠道：INAPP / EMAIL / SMS / IM */
    private String channel;

    /** 接收标识：用户ID / 邮箱 / 手机号 */
    private String recipient;

    /** 投递状态：SENT / DELIVERED / READ / BOUNCED / FAILED / SKIPPED / MOCKED */
    private String status;

    /** 渠道回执标识（可选） */
    private String providerMessageId;

    /**
     * 失败/跳过原因摘要。
     *
     * <p>{@code ALWAYS} 更新策略：重试成功或回执送达时需要把历史错误清成 NULL，
     * 默认 {@code NOT_NULL} 会跳过写 NULL，导致状态已成功却残留旧错误。</p>
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String errorMessage;

    /** 尝试次数 */
    private Integer attempts;

    /** 投递时间 */
    private LocalDateTime sentAt;

    /** 渠道确认送达时间（回执态写入） */
    private LocalDateTime deliveredAt;

    /** 接收人阅读时间（回执态写入，仅可回传阅读态的渠道） */
    private LocalDateTime readAt;

    /** 站内信已读标记：1 已读 / 0 未读（非 INAPP 渠道恒为 0） */
    private Integer readFlag;
}
