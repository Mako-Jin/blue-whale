package com.bluewhale.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSE {@code done} 事件：回答结束，返回落库消息ID、token 与引用。
 *
 * @author BlueWhale
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatDoneVO {

    /** 会话ID（字符串） */
    private String sessionId;

    /** 助手ID（字符串） */
    private String assistantId;

    /** 助手消息ID（字符串） */
    private String messageId;

    /** token 统计（whale-ai 估算值） */
    private int tokens;

    /** 是否降级（无模型时返回检索摘要） */
    private boolean degraded;

    /** 执行耗时（毫秒） */
    private long elapsedMillis;

    /** 引用清单 */
    private java.util.List<CitationVO> citations;
}
