package com.bluewhale.monitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 服务器监控启动类
 * 基于 Spring Boot Admin + Actuator
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
public class WhaleMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(WhaleMonitorApplication.class, args);
    }
}
