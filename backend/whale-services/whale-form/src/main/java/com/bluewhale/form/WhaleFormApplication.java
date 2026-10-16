package com.bluewhale.form;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 表单管理启动类
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
public class WhaleFormApplication {

    public static void main(String[] args) {
        SpringApplication.run(WhaleFormApplication.class, args);
    }
}
