package com.bluewhale.pay;

import com.bluewhale.pay.config.PayChannelProperties;
import com.bluewhale.pay.config.PayCloseProperties;
import com.bluewhale.pay.config.PayNotifyProperties;
import com.bluewhale.pay.config.PayReconcileProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 支付服务启动类
 *
 * <p>默认 {@code whale.pay.mock=true}：支付/退款/对账只做本地模拟，
 * 不发起真实资金请求。渠道调用已移出本地事务——关闭 mock 接入真实渠道后，
 * 渠道失败回退支付单、失败退款留 {@code FAILED} 单作为对账入口。</p>
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties({PayChannelProperties.class, PayNotifyProperties.class,
        PayReconcileProperties.class, PayCloseProperties.class})
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
