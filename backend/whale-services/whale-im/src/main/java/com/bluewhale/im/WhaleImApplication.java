package com.bluewhale.im;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * WebIM 服务启动类（Wave 5.52）。
 *
 * <p>服务端口 8091，对外一级路径前缀 {@code /im}（经网关 {@code lb://whale-im} 转发，不剥离前缀）；
 * WebSocket 升级路径 {@code /im/ws}，由 {@code lb:ws://whale-im} 路由承接。
 * 职责：会话与消息、已读/未读、在线状态、机器人会话；数据落 {@code bluewhale_im}，
 * Redis 承载消息序号、WS ticket 与在线状态。</p>
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
public class WhaleImApplication {

    /**
     * 应用启动入口
     *
     * @param args 启动命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhaleImApplication.class, args);
    }
}
