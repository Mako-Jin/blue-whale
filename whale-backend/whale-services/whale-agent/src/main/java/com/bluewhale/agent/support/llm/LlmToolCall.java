package com.bluewhale.agent.support.llm;

/**
 * 模型请求的工具调用（OpenAI {@code tool_calls[]} 元素）。
 *
 * @param id        调用ID（用于回填工具结果）
 * @param name      工具名
 * @param arguments 参数 JSON 字符串
 * @author BlueWhale
 */
public record LlmToolCall(String id, String name, String arguments) {
}
