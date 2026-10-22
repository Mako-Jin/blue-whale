package com.bluewhale.agent.support.llm;

/**
 * 对话消息（OpenAI chat 格式）。
 *
 * @param role    角色：system / user / assistant
 * @param content 内容
 * @author BlueWhale
 */
public record ChatMessage(String role, String content) {

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
}
