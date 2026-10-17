package com.bluewhale.file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 文件服务启动类
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
public class WhaleFileApplication {

    /**
     * 应用启动入口
     *
     * @param args 启动命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhaleFileApplication.class, args);
    }
}
