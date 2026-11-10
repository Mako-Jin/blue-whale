package com.bluewhale.agent.support.llm;

import java.util.List;

/**
 * 带工具定义的单轮模型响应。
 *
 * @param content   文本内容（模型决定调用工具时通常为空）
 * @param toolCalls 工具调用列表（空表示本轮无需再调用工具）
 * @author BlueWhale
 */
public record LlmToolTurn(String content, List<LlmToolCall> toolCalls) {

    /**
     * 构造仅含文本的响应。
     *
     * @param content 文本
     * @return 响应
     */
    public static LlmToolTurn text(String content) {
        return new LlmToolTurn(content, List.of());
    }

    /**
     * 是否存在待执行的工具调用。
     *
     * @return 存在返回 {@code true}
     */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
