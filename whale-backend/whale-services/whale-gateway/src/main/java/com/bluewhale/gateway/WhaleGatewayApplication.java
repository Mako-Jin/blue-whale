package com.bluewhale.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * API 网关启动类
 *
 * <p>{@code @EnableScheduling}：启用白名单动态刷新（{@code GatewayWhitelistProvider} 定时拉取）。</p>
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
public class WhaleGatewayApplication {

    /**
     * 启动 API 网关应用
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhaleGatewayApplication.class, args);
    }
}
