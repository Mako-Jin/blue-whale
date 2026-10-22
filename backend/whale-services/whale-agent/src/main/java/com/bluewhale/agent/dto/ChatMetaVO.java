package com.bluewhale.agent.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * SSE {@code meta} 事件：流式回答开始前下发会话、意图与证据清单。
 *
 * @author BlueWhale
 */
@Data
@Builder
public class ChatMetaVO {

    /** 会话ID（字符串） */
    private String conversationId;

    /** 意图：STRUCTURED / SEMANTIC / MIXED */
    private String intent;

    /** 是否降级（未配置模型时为 {@code true}） */
    private boolean degraded;

    /** 证据引用清单 */
    private List<CitationVO> citations;
}
