package com.bluewhale.message.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内信收件箱条目视图。
 *
 * @author BlueWhale
 */
@Data
public class InAppMessageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 投递记录ID */
    private Long deliveryId;

    /** 消息ID */
    private Long messageId;

    /** 标题 */
    private String title;

    /** 正文 */
    private String content;

    /** 业务类型 */
    private String bizType;

    /** 业务标识 */
    private String bizId;

    /** 投递状态 */
    private String deliveryStatus;

    /** 已读标记：1 已读 / 0 未读 */
    private Integer readFlag;

    /** 投递时间 */
    private LocalDateTime sentAt;

    /** 创建时间 */
    private LocalDateTime createTime;
}
