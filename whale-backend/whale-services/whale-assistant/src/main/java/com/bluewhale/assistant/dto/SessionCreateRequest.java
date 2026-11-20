package com.bluewhale.assistant.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 会话创建请求。
 *
 * @author BlueWhale
 */
@Data
public class SessionCreateRequest {

    /** 助手ID */
    @NotNull(message = "{validation.assistant.session.assistant-id-required}")
    private Long assistantId;

    /** 来源：platform / embed / openapi（缺省 platform） */
    @Size(max = 32, message = "{validation.assistant.session.source-size}")
    private String source;

    /** 业务侧会话引用（可空） */
    @Size(max = 128, message = "{validation.assistant.session.channel-ref-size}")
    private String channelRef;
}
