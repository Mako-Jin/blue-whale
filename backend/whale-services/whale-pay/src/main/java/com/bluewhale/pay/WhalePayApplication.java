package com.bluewhale.pay;

import com.bluewhale.pay.config.PayChannelProperties;
import com.bluewhale.pay.config.PayNotifyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 支付服务启动类
 *
 * <p>默认 {@code whale.pay.mock=true}：支付/退款/对账只做本地模拟，
 * 不发起真实资金请求；生产接入真实渠道时需实现适配器并显式关闭 mock。</p>
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties({PayChannelProperties.class, PayNotifyProperties.class})
public class WhalePayApplication {

    /**
     * 应用启动入口
     *
     * @param args 启动命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhalePayApplication.class, args);
    }
}
