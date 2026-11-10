package com.bluewhale.pay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 支付渠道配置。
 *
 * <p>{@link #mock} 为全局开关：开启时所有渠道只做本地模拟，不发起真实资金请求，
 * 渠道流水号以 {@code MOCK-} 开头；关闭时必须先接入真实渠道适配器，否则支付/退款/对账
 * 会返回明确业务错误，避免把未发生的资金动作误记为成功。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.pay")
public class PayChannelProperties {

    /** 全局 mock 开关：true 时不发起真实渠道请求 */
    private boolean mock = true;

    /** 允许的渠道编码（大写），用于创建支付单时校验 */
    private List<String> allowedChannels = List.of("MOCK", "BALANCE", "ALIPAY", "WECHAT", "UNIONPAY");
}
