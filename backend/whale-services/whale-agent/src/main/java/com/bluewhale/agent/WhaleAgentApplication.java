package com.bluewhale.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 平台与项目知识 Agent 启动类（Wave 5.47）。
 *
 * <p>服务端口 8090，对外一级路径前缀 {@code /agent}（经网关 {@code lb://whale-agent} 转发，不剥离前缀）。
 * 职责：仓库知识采集与 Lucene 索引、结构化事实查询、检索调试与（后续）LLM 问答编排。</p>
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableAsync
public class WhaleAgentApplication {

    /**
     * 应用启动入口
     *
     * @param args 启动命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhaleAgentApplication.class, args);
    }
}
