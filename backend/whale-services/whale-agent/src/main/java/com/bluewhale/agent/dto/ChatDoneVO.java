package com.bluewhale.agent.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * SSE {@code done} 事件：回答结束，返回落库消息ID、token 与引用。
 *
 * @author BlueWhale
 */
@Data
@Builder
public class ChatDoneVO {

    /** 会话ID（字符串） */
    private String conversationId;

    /** 助手消息ID（字符串） */
    private String messageId;

    /** token 统计（估算，严格用量需模型返回 usage） */
    private int tokens;

    /** 是否降级（无模型时返回检索摘要） */
    private boolean degraded;

    /** 执行耗时（毫秒） */
    private long elapsedMillis;

    /** 引用清单 */
    private List<CitationVO> citations;
}
