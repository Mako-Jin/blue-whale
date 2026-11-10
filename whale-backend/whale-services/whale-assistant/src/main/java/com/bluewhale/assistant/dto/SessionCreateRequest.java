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
    @NotNull(message = "助手ID不能为空")
    private Long assistantId;

    /** 来源：platform / embed / openapi（缺省 platform） */
    @Size(max = 32, message = "来源长度不能超过 32 字")
    private String source;

    /** 业务侧会话引用（可空） */
    @Size(max = 128, message = "业务侧引用长度不能超过 128 字")
    private String channelRef;
}
