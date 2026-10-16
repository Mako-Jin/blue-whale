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

    public static void main(String[] args) {
        SpringApplication.run(WhaleAuditApplication.class, args);
    }
}
