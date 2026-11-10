package com.bluewhale.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * ERP 企业资源计划启动类
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class WhaleErpApplication {

    /**
     * 应用启动入口
     *
     * @param args 启动命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhaleErpApplication.class, args);
    }
}
