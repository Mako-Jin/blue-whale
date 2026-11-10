package com.bluewhale.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 开放 API 问答请求（业务应用服务对服务调用）。
 *
 * <p>鉴权：内网令牌 {@code X-Internal-Token}（网关不参与，业务后端经 Nacos 直连本服务）；
 * 最终用户身份以 {@code X-User-Id} 透传，权限码以 {@code X-Permission-Codes} 可选透传。</p>
 *
 * @author BlueWhale
 */
@Data
public class OpenChatRequest {

    /** 助手编码（须为已发布助手） */
    @NotBlank(message = "助手编码不能为空")
    @Size(max = 64, message = "助手编码长度不能超过 64 字")
    private String assistantCode;

    /** 用户问题 */
    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题长度不能超过 2000 字")
    private String question;

    /** 既有会话ID（可空；为空自动新建 openapi 来源会话，传入则续聊且须归属当前用户） */
    private Long sessionId;

    /** 业务侧会话引用（可空，随会话落库便于回传关联） */
    @Size(max = 128, message = "业务侧引用长度不能超过 128 字")
    private String channelRef;
}
