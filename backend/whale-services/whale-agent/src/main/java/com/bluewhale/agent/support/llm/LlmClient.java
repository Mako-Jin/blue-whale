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
     * 流式生成回答（阻塞读取，逐增量回调）。
     *
     * @param messages 消息列表
     * @param onDelta  增量回调
     * @throws IOException 网络或响应异常
     */
    void stream(List<ChatMessage> messages, Consumer<String> onDelta) throws IOException;
}
