package com.bluewhale.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 配置中心启动类
 * 基于 Nacos（默认）或 Apollo，提供统一的配置管理接口
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
public class WhaleConfigApplication {

    public static void main(String[] args) {
        SpringApplication.run(WhaleConfigApplication.class, args);
    }
}
