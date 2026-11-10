package com.bluewhale.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 助手平台配置项（{@code whale.assistant.*}）。
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.assistant")
public class AssistantProperties {

    /** whale-ai 内网编排配置 */
    private final Ai ai = new Ai();

    /** 单会话消息条数上限（防超长会话拖垮落库） */
    private int sessionMaxMessages = 200;

    /** 单条消息正文字节数上限 */
    private int messageMaxBytes = 131072;

    /**
     * whale-ai 内网调用配置。
     *
     * @author BlueWhale
     */
    @Data
    public static class Ai {

        /** whale-ai 基地址（仅内网，不注册网关/Nacos） */
        private String baseUrl = "http://127.0.0.1:8092";

        /** 连接超时（毫秒） */
        private int connectTimeoutMillis = 5000;

        /** 问答总超时（秒） */
        private int timeoutSeconds = 120;
    }
}
