package com.bluewhale.message.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 发送结果视图。
 *
 * @author BlueWhale
 */
@Data
public class SendMessageResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息ID */
    private Long messageId;

    /** 是否为幂等命中（true 时未重复投递） */
    private boolean duplicate;

    /** 消息状态 */
    private String status;

    /** 投递明细 */
    private List<MessageDeliveryVO> deliveries;
}
