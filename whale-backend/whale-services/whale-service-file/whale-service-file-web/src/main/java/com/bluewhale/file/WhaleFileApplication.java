package com.bluewhale.file;

import com.bluewhale.file.config.FileProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 文件服务启动类
 *
 * <p>存储插件各自通过 Spring Boot 自动配置注册属性与 Bean，本类仅显式启用服务自身的
 * {@link FileProperties}，避免属性扫描与插件自动配置重复注册。</p>
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableConfigurationProperties(FileProperties.class)
@EnableDiscoveryClient
@EnableScheduling
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
