package com.bluewhale.pay.channel;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pay.config.PayChannelProperties;
import com.bluewhale.pay.entity.PayOrder;
import com.bluewhale.pay.enums.PayOrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.UUID;

/**
 * 支付渠道网关（mock 实现）。
 *
 * <p>当前无真实渠道供应商，默认 {@code whale.pay.mock=true}：支付/退款只生成模拟渠道流水号，
 * 查询直接镜像本地状态。关闭 mock 后因为没有真实适配器，三类动作都会抛出明确的
 * {@code PARAM_ERROR}，避免真实资金动作被伪造为成功。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class PayChannelGateway {

    /** 模拟渠道流水号前缀 */
    private static final String MOCK_TRADE_PREFIX = "MOCK-";


    private final PayChannelProperties properties;

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
        ensureMock(order);
        return new PayChannelResult(newMockTradeNo(), PayOrderStatus.PAID.code());
    }

    /**
     * 发起退款
     *
     * @param order 支付单
     * @return 渠道结果
     */
    public PayChannelResult refund(PayOrder order) {
        ensureMock(order);
        String providerTradeNo = StringUtils.hasText(order.getChannelTradeNo())
                ? order.getChannelTradeNo() : newMockTradeNo();
        return new PayChannelResult(providerTradeNo, PayOrderStatus.REFUNDED.code());
    }

    /**
     * 查询渠道状态（mock 模式镜像本地状态）
     *
     * @param order 支付单
     * @return 渠道状态
     */
    public String queryStatus(PayOrder order) {
        ensureMock(order);
        return order.getStatus();
    }

    /**
     * mock 前置校验：未开启 mock 时没有真实适配器，直接拒绝
     *
     * @param order 支付单
     */
    private void ensureMock(PayOrder order) {
        if (!properties.isMock()) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "真实支付渠道尚未接入，请先实现渠道适配器或开启 whale.pay.mock");
        }
        if (!isSupported(order.getChannel())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的支付渠道: " + order.getChannel());
        }
    }

    /**
     * 生成模拟渠道流水号（长度 37，符合库表 64 限制）
     *
     * @return 模拟流水号
     */
    private static String newMockTradeNo() {
        return MOCK_TRADE_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }
}
