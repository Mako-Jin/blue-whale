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

    /**
     * 回答模式：空/{@code agentic} = 现状（模型自主多轮调工具）；{@code direct} = 跳过工具编排，
     * 直接以「召回证据 + 提示词」单轮生成。
     *
     * <p>小参数本地模型（如 qwen2.5:1.5B）在工具轮次里常把 tool_calls 当正文吐出来，导致回答
     * 只剩一句「需要检索。」；需要稳定成段回答的调用方（如 3D 城的 NPC 问话）显式传 direct。</p>
     */
    @Size(max = 16)
    private String mode;

    /**
     * 角色设定（仅 {@code direct} 模式生效）：影响自称与口吻，不改变回答规则与引用要求。
     */
    @Size(max = 600)
    private String persona;

    /**
     * 是否走直答模式。
     *
     * @return 传 {@code direct}（忽略大小写）时为 true
     */
    public boolean isDirectMode() {
        return mode != null && "direct".equalsIgnoreCase(mode.trim());
    }
}
