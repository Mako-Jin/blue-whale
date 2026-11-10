package com.bluewhale.assistant.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 开放 API 问答结果（同步聚合 whale-ai SSE 的最终回答）。
 *
 * @author BlueWhale
 */
@Getter
@Builder
public class OpenChatVO {

    /** 会话ID（新建或续聊的会话，业务侧可保存用于续聊） */
    private String sessionId;

    /** 助手ID */
    private String assistantId;

    /** 本次回答消息ID */
    private String messageId;

    /** 回答正文 */
    private String answer;

    /** 引用清单（已由 whale-ai 校验，与正文 [S1] 标记一致） */
    private List<CitationVO> citations;

    /** token 统计 */
    private int tokens;

    /** 是否降级（模型未配置或执行异常时的检索摘要回落） */
    private boolean degraded;

    /** 耗时（毫秒） */
    private int elapsedMillis;
}
