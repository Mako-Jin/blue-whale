package com.bluewhale.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API 网关启动类
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
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
