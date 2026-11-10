package com.bluewhale.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 提问请求（{@code POST /agent/chat}）。
 *
 * <p>{@code conversationId} 为空时新建会话；{@code scope} 为检索范围（逗号分隔
 * {@code code,doc,sql,config,git,runtime}，空表示全部）。</p>
 *
 * @author BlueWhale
 */
@Data
public class ChatRequest {

    /** 会话ID（可空，空表示新建会话） */
    private Long conversationId;

    /** 用户问题 */
    @NotBlank
    @Size(max = 2000)
    private String question;

    /** 检索范围（逗号分隔，可空） */
    @Size(max = 128)
    private String scope;
}
