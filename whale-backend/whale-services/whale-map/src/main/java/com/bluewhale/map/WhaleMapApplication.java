package com.bluewhale.map;

import com.bluewhale.map.config.MapProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 地图服务启动类。
 *
 * <p>本服务是「地图能力出口」，不是底图渲染服务：底图由前端 SDK 自己画，服务端只负责
 * 地址与坐标的互转、点位落库与检索。这样切供应商时前端只换 SDK 不换接口，
 * Key 与坐标系也收敛在服务端。</p>
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties(MapProperties.class)
public class WhaleMapApplication {

    /**
     * 应用启动入口
     *
     * @param args 启动命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhaleMapApplication.class, args);
    }
}
