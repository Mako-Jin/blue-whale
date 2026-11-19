package com.bluewhale.message.channel;

import com.bluewhale.message.dto.MessageRecipient;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 渠道发送上下文。
 *
 * @author BlueWhale
 */
@Data
@Builder
public class ChannelMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息ID */
    private Long messageId;

    /** 租户ID */
    private Long tenantId;

    /** 业务类型 */
    private String bizType;

    /** 业务标识 */
    private String bizId;

    /** 标题 */
    private String title;

    /** 正文 */
    private String content;

    /** 模板变量 */
    private Map<String, Object> payload;

    /** 当前接收人 */
    private MessageRecipient recipient;
}
