package com.bluewhale.i18n;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 国际化消息管理服务启动类
 *
 * <p>承载 {@code whale-i18n-sdk} 的消息 CRUD 接口；SDK 的实体 / Mapper /
 * 缓存清理助手 / 三级回退 MessageSource 均由 SDK 自动装配提供。</p>
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
public class WhaleI18nApplication {

    /**
     * 应用启动入口
     *
     * @param args 启动命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhaleI18nApplication.class, args);
    }
}
