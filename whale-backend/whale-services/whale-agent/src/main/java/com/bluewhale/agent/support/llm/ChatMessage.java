package com.bluewhale.agent.support.llm;

import java.util.List;

/**
 * 对话消息（OpenAI chat 格式）。
 *
 * <p>除普通 {@code system/user/assistant} 文本消息外，支持工具调用场景：
 * 助手消息可携带 {@code toolCalls}，工具结果消息使用 {@code role=tool} 并回填 {@code toolCallId}。</p>
 *
 * @param role       角色：system / user / assistant / tool
 * @param content    内容（工具调用阶段的助手消息可为 {@code null}）
 * @param toolCallId 工具结果对应的调用ID（仅 {@code role=tool}）
 * @param toolCalls  助手发起的工具调用（仅 {@code role=assistant}）
 * @author BlueWhale
 */
public record ChatMessage(String role, String content, String toolCallId, List<LlmToolCall> toolCalls) {

    /**
     * 构造纯文本消息（兼容既有用法）。
     *
     * @param role    角色
     * @param content 内容
     */
    public ChatMessage(String role, String content) {
        this(role, content, null, null);
    }

    /**
     * 构造用户消息。
     *
     * @param content 内容
     * @return 消息
     */
    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    /**
     * 构造助手消息。
     *
     * @param content 内容
     * @return 消息
     */
    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }

    /**
     * 构造系统消息。
     *
     * @param content 内容
     * @return 消息
     */
    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    /**
     * 构造携带工具调用的助手消息。
     *
     * @param content   文本内容（可为 {@code null}）
     * @param toolCalls 工具调用列表
     * @return 消息
     */
    public static ChatMessage assistantToolCalls(String content, List<LlmToolCall> toolCalls) {
        return new ChatMessage("assistant", content, null, toolCalls);
    }

    /**
     * 构造工具结果消息。
     *
     * @param toolCallId 调用ID
     * @param content    工具输出（文本）
     * @return 消息
     */
    public static ChatMessage tool(String toolCallId, String content) {
        return new ChatMessage("tool", content, toolCallId, null);
    }
}
