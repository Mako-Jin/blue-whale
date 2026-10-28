package com.bluewhale.message.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 投递记录视图。
 *
 * @author BlueWhale
 */
@Data
public class MessageDeliveryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 投递记录ID */
    private Long id;

    /** 渠道 */
    private String channel;

    /** 接收标识 */
    private String recipient;

    /** 投递状态 */
    private String status;

    /** 渠道回执标识 */
    private String providerMessageId;

    /** 失败/跳过原因 */
    private String errorMessage;

    /** 尝试次数 */
    private Integer attempts;

    /** 投递时间 */
    private LocalDateTime sentAt;
}
