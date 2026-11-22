package com.bluewhale.pay.config;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * 支付渠道配置。
 *
 * <p>{@link #mock} 为全局开关：开启时所有渠道只做本地模拟，不发起真实资金请求，
 * 渠道流水号以 {@code MOCK-} 开头；关闭表示接入真实渠道，此时渠道调用已在本地事务之外，
 * 支付/退款失败分别回退为 {@code CREATED} 或留下 {@code FAILED} 单供对账。</p>
 *
 * @author BlueWhale
 */
@Data
@Validated
@ConfigurationProperties(prefix = "whale.pay")
public class PayChannelProperties {

    /** 全局 mock 开关：true 时不发起真实渠道请求 */
    private boolean mock = true;

    /**
     * 允许的渠道编码（大写），用于创建支付单时校验。
     *
     * <p>默认只放行已内置适配器的 MOCK；新增真实渠道时必须同时实现适配器并在此登记，
     * 否则 {@link com.bluewhale.pay.channel.PayChannelGateway} 启动即失败。</p>
     */
    @NotEmpty
    private List<String> allowedChannels = List.of("MOCK");
}
