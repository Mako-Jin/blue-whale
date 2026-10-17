package com.bluewhale.pay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 支付服务启动类
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
public class WhalePayApplication {

    /**
     * 应用启动入口
     *
     * @param args 启动命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhalePayApplication.class, args);
    }
}
