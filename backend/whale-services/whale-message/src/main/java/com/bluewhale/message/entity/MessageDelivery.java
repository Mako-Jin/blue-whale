package com.bluewhale.message.entity;

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

    /** 投递状态：SENT / FAILED / SKIPPED / MOCKED */
    private String status;

    /** 渠道回执标识（可选） */
    private String providerMessageId;

    /** 失败/跳过原因摘要 */
    private String errorMessage;

    /** 尝试次数 */
    private Integer attempts;

    /** 投递时间 */
    private LocalDateTime sentAt;

    /** 站内信已读标记：1 已读 / 0 未读（非 INAPP 渠道恒为 0） */
    private Integer readFlag;
}
