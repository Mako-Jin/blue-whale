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

    public static void main(String[] args) {
        SpringApplication.run(WhaleGatewayApplication.class, args);
    }
}
