package com.bluewhale.job;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 定时任务管理启动类
 *
 * @author BlueWhale
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableDiscoveryClient
public class WhaleJobApplication {

    /**
     * 应用启动入口
     *
     * @param args 启动命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhaleJobApplication.class, args);
    }
}
