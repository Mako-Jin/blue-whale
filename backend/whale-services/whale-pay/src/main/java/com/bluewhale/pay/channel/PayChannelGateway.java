package com.bluewhale.pay.channel;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pay.config.PayChannelProperties;
import com.bluewhale.pay.entity.PayOrder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 支付渠道网关（适配器路由）。
 *
 * <p>不再自带渠道逻辑：按渠道号路由到 {@link PayChannelAdapter} 实现。
 * 渠道还必须在 {@code whale.pay.allowed-channels} 允许列表内，双重校验。
 * 渠道存在但无适配器（如未接真实渠道却传 ALIPAY）抛 {@code PARAM_ERROR}。</p>
 *
 * @author BlueWhale
 */
@Component
public class PayChannelGateway {

    private final PayChannelProperties properties;

    private final Map<String, PayChannelAdapter> adapters;

    /**
     * 构造网关并按渠道号建立路由表。
     *
     * @param properties 渠道配置
     * @param adapters   容器内全部适配器实现
     */
    public PayChannelGateway(PayChannelProperties properties, java.util.List<PayChannelAdapter> adapters) {
        this.properties = properties;
        this.adapters = adapters == null ? Map.of() : adapters.stream()
                .collect(Collectors.toMap(adapter -> adapter.channel().toUpperCase(Locale.ROOT),
                        Function.identity(), (a, b) -> a));
    }

    /**
     * 判断渠道编码是否在允许列表中（不区分大小写）
     *
     * @param channel 渠道编码
     * @return 是否允许
     */
    public boolean isSupported(String channel) {
        if (!StringUtils.hasText(channel)) {
            return false;
        }
        String normalized = channel.trim().toUpperCase(Locale.ROOT);
        return properties.getAllowedChannels().stream()
                .filter(StringUtils::hasText)
                .map(item -> item.trim().toUpperCase(Locale.ROOT))
                .anyMatch(normalized::equals);
    }

    /**
     * 发起支付
     *
     * @param order 支付单
     * @return 渠道结果
     */
    public PayChannelResult pay(PayOrder order) {
        return requireAdapter(order.getChannel()).pay(order);
    }

    /**
     * 发起退款
     *
     * @param order        支付单
     * @param refundNo     退款单号
     * @param refundAmount 退款金额
     * @return 渠道结果
     */
    public PayChannelResult refund(PayOrder order, String refundNo, BigDecimal refundAmount) {
        return requireAdapter(order.getChannel()).refund(order, refundNo, refundAmount);
    }

    /**
     * 查询渠道状态（mock 适配器镜像本地状态）
     *
     * @param order 支付单
     * @return 渠道状态
     */
    public String queryStatus(PayOrder order) {
        return requireAdapter(order.getChannel()).queryStatus(order);
    }

    /**
     * 按渠道号取适配器；白名单通过但无适配器实现视为渠道未接入
     *
     * @param channel 渠道编码
     * @return 适配器
     */
    private PayChannelAdapter requireAdapter(String channel) {
        if (!isSupported(channel)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的支付渠道: " + channel);
        }
        PayChannelAdapter adapter = adapters.get(channel.trim().toUpperCase(Locale.ROOT));
        if (adapter == null) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "渠道未接入适配器: " + channel + "（当前仅内置 MOCK）");
        }
        return adapter;
    }
}
