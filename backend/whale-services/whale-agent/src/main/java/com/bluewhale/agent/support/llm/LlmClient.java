package com.bluewhale.agent.support.llm;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * OpenAI 兼容对话客户端抽象：支持一次性与流式回答。
 *
 * @author BlueWhale
 */
public interface LlmClient {

    /**
     * 是否可用（开启且配置了 API Key）。
     *
     * @return 可用返回 {@code true}
     */
    boolean available();

    /**
     * 一次性生成回答。
     *
     * @param messages 消息列表
     * @return 回答文本；失败返回 {@code null}
     */
    String complete(List<ChatMessage> messages);

    /**
     * 带工具定义的单轮生成（供自主编排循环使用）。
     *
     * <p>默认实现返回 {@code null} 表示不支持工具调用，调用方据此回落到单轮 RAG；
     * OpenAI 兼容客户端覆写该方法。</p>
     *
     * @param messages 消息列表（可含 assistant tool_calls 与 tool 结果消息）
     * @param tools    工具定义
     * @return 单轮响应；失败或不支持返回 {@code null}
     */
    default LlmToolTurn completeWithTools(List<ChatMessage> messages, List<LlmToolSpec> tools) {
        return null;
    }

    /**
     * 流式生成回答（阻塞读取，逐增量回调）。
     *
     * @param messages 消息列表
     * @param onDelta  增量回调
     * @throws IOException 网络或响应异常
     */
    void stream(List<ChatMessage> messages, Consumer<String> onDelta) throws IOException;
}
