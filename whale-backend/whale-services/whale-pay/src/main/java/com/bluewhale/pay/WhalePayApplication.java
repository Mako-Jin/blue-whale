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
 * 不发起真实资金请求；关闭 mock 会被 {@code PayChannelStartupGuard} 拒绝启动，
 * 直到渠道调用移出本地事务并补齐补偿/对账。</p>
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
