package com.bluewhale.pm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 项目管理服务启动类
 *
 * @author BlueWhale
 */
@EnableAsync
@SpringBootApplication
@EnableDiscoveryClient
public class WhalePmApplication {

    /**
     * 应用启动入口
     *
     * @param args 启动命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhalePmApplication.class, args);
    }
}
