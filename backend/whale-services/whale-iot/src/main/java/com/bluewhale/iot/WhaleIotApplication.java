package com.bluewhale.iot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * IoT 设备联网平台服务启动类（Wave 6.02）。
 *
 * <p>服务端口 8088，对外一级路径前缀 {@code /iot}（经网关 {@code lb://whale-iot}
 * 转发，不剥离前缀）。</p>
 *
 * <p>职责边界：设备接入与认证（EMQX 认证 Webhook 提供方）、物模型
 * （属性/事件/服务）、遥测上下行、设备影子、指令下发与回执、规则引擎
 * （条件→动作，告警动作对接 whale-alert 9103 内网接口）。不做业务语义
 * （MES 设备台账仍归 MES），协议网关（Modbus/HTTP 等）留 M4 之后。</p>
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
public class WhaleIotApplication {

    /**
     * 应用启动入口
     *
     * @param args 启动命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhaleIotApplication.class, args);
    }
}
