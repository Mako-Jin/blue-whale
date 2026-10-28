package com.bluewhale.message;

import com.bluewhale.message.config.MessageChannelProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 统一消息服务启动类。
 *
 * <p>M0 提供模板渲染、渠道路由、幂等与投递记录；SMS/EMAIL/IM 为可选适配器，
 * 默认 {@code whale.message.mock=true}，无真实供应商时只记录 {@code MOCKED/SKIPPED}。</p>
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties(MessageChannelProperties.class)
public class WhaleMessageApplication {

    /**
     * 应用启动入口
     *
     * @param args 启动命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhaleMessageApplication.class, args);
    }
}
