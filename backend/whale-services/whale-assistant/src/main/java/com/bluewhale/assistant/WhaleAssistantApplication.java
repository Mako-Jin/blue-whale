package com.bluewhale.assistant;

import com.bluewhale.assistant.config.AssistantProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * AI 助手平台服务启动类（Wave 6.01）。
 *
 * <p>服务端口 8087（原 whale-i18n 释放空位），对外一级路径前缀 {@code /assistant}
 * （经网关 {@code lb://whale-assistant} 转发，不剥离前缀）。</p>
 *
 * <p>职责边界：助手定义与生命周期、会话与消息持久化、工具授权、面向业务应用的
 * 开放 API 与用量统计。LLM 编排仍由 {@code whale-ai}（内网 8092，不注册网关/Nacos）
 * 承担，知识检索由 {@code whale-agent}（8090）承担；本服务不直连任何模型凭据。</p>
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties(AssistantProperties.class)
public class WhaleAssistantApplication {

    /**
     * 应用启动入口
     *
     * @param args 启动命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhaleAssistantApplication.class, args);
    }
}
