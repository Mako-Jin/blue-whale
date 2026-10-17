package com.bluewhale.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 审计日志启动类
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
public class WhaleAuditApplication {

    /**
     * 启动审计日志应用
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhaleAuditApplication.class, args);
    }
}
