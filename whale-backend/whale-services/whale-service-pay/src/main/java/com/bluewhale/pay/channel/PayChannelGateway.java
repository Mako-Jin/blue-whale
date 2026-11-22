package com.bluewhale.pay.channel;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pay.config.PayChannelProperties;
import com.bluewhale.pay.po.PayOrderPo;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 支付渠道网关（适配器路由）。
 *
 * <p>按渠道号路由到 {@link PayChannelAdapter} 实现。一个渠道可用必须同时满足两点：
 * 在 {@code whale.pay.allowed-channels} 白名单内，且存在对应适配器实现。
 * 白名单中登记了却没有适配器的渠道在启动阶段直接失败，避免「创建成功但永远付不了」的支付单。</p>
 *
 * @author BlueWhale
 */
@Component
public class PayChannelGateway {

    private final PayChannelProperties properties;

    private final Map<String, PayChannelAdapter> adapters;

    /**
     * 构造网关并按渠道号建立路由表；重复渠道与未接入适配器的白名单渠道启动即失败。
     *
     * @param properties 渠道配置
     * @param adapters   容器内全部适配器实现
     */
    public PayChannelGateway(PayChannelProperties properties, List<PayChannelAdapter> adapters) {
        this.properties = properties;
        this.adapters = adapters == null ? Map.of() : adapters.stream()
                .collect(Collectors.toMap(adapter -> adapter.channel().toUpperCase(Locale.ROOT),
                        Function.identity(), (first, second) -> {
                            throw new IllegalStateException("重复的支付渠道适配器: " + second.channel());
                        }));
        validateAllowedChannels();
    }

    /**
     * 启动校验：白名单中的每个渠道都必须有适配器，否则直接失败。
     */
    private void validateAllowedChannels() {
        for (String channel : properties.getAllowedChannels()) {
            if (!StringUtils.hasText(channel)) {
                continue;
            }
            String normalized = channel.trim().toUpperCase(Locale.ROOT);
            if (!adapters.containsKey(normalized)) {
                throw new IllegalStateException("渠道 " + normalized
                        + " 已登记在 whale.pay.allowed-channels，但未实现对应 PayChannelAdapter");
            }
        }
    }

    /**
     * 判断渠道是否可用：白名单 + 适配器同时存在（不区分大小写）。
     *
     * @param channel 渠道编码
     * @return 是否可用
     */
    public boolean isSupported(String channel) {
        if (!StringUtils.hasText(channel)) {
            return false;
        }
        String normalized = channel.trim().toUpperCase(Locale.ROOT);
        return matchesAllowed(normalized) && adapters.containsKey(normalized);
    }

    /**
     * 校验渠道可用，不可用时抛出明确业务异常。
     *
     * @param channel 渠道编码
     */
    public void ensureAvailable(String channel) {
        if (!StringUtils.hasText(channel)) {
            throw unsupported(channel);
        }
        String normalized = channel.trim().toUpperCase(Locale.ROOT);
        if (!matchesAllowed(normalized)) {
            throw unsupported(channel);
        }
        if (!adapters.containsKey(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.channel.adapter-missing",
                    "渠道未接入适配器: " + channel + "（当前仅内置 MOCK）", channel);
        }
    }

    /**
     * 发起支付
     *
     * @param order 支付单
     * @return 渠道结果
     */
    public PayChannelResult pay(PayOrderPo order) {
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
    public PayChannelResult refund(PayOrderPo order, String refundNo, BigDecimal refundAmount) {
        return requireAdapter(order.getChannel()).refund(order, refundNo, refundAmount);
    }

    /**
     * 查询渠道状态（mock 适配器镜像本地状态）
     *
     * @param order 支付单
     * @return 渠道状态
     */
    public String queryStatus(PayOrderPo order) {
        return requireAdapter(order.getChannel()).queryStatus(order);
    }

    /**
     * 解析渠道回调并验签（验签在适配器内完成）。
     *
     * @param channel 渠道编码
     * @param rawBody 原始报文
     * @param headers 原始请求头
     * @return 统一回调请求
     */
    public ChannelCallbackRequest parseCallback(String channel, String rawBody, Map<String, String> headers) {
        return requireAdapter(channel).parseCallback(rawBody, headers);
    }

    /**
     * 渠道要求的成功响应体。
     *
     * @param channel 渠道编码
     * @return 成功响应体
     */
    public String successResponse(String channel) {
        return requireAdapter(channel).successResponse();
    }

    /**
     * 渠道要求的失败响应体（触发渠道重发）。
     *
     * @param channel 渠道编码
     * @return 失败响应体
     */
    public String failureResponse(String channel) {
        return requireAdapter(channel).failureResponse();
    }

    /**
     * 按渠道号取适配器；白名单通过但无适配器实现视为渠道未接入
     *
     * @param channel 渠道编码
     * @return 适配器
     */
    private PayChannelAdapter requireAdapter(String channel) {
        ensureAvailable(channel);
        return adapters.get(channel.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * 渠道是否在允许列表中
     *
     * @param normalized 已大写的渠道编码
     * @return 是否允许
     */
    private boolean matchesAllowed(String normalized) {
        return properties.getAllowedChannels().stream()
                .filter(StringUtils::hasText)
                .map(item -> item.trim().toUpperCase(Locale.ROOT))
                .anyMatch(normalized::equals);
    }

    /**
     * 构造不支持的渠道异常
     *
     * @param channel 渠道编码
     * @return 业务异常
     */
    private static BizException unsupported(String channel) {
        return new BizException(ErrorCode.PARAM_ERROR, "pay.channel.unsupported",
                "不支持的支付渠道: " + channel, channel);
    }
}
