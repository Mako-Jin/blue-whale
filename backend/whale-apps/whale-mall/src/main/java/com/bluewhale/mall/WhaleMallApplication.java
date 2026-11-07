package com.bluewhale.mall;

import com.bluewhale.mall.config.MallProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 商城应用启动类（Wave 5.94，M0）。
 *
 * <p>四个业务上下文（catalog / channel / cart / order）共用一个模块化单体。
 * 服务边界：支付走 {@code whale-pay}，素材字节归 {@code whale-file}，通知归
 * {@code whale-message}；商品主数据由本服务自建，<b>不与 {@code whale-erp} 实时双写</b>。</p>
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties(MallProperties.class)
public class WhaleMallApplication {

    /**
     * 应用启动入口。
     *
     * @param args 启动命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhaleMallApplication.class, args);
    }
}
