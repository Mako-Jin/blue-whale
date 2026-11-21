package com.bluewhale.pay.channel;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pay.config.PayChannelProperties;
import com.bluewhale.pay.po.PayOrderPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

/**
 * MOCK 渠道适配器。
 *
 * <p>仅当 {@code whale.pay.mock=true} 时可用；流水号为
 * {@code MOCK-<uuid>} 形式，支付/退款即时成功。真实渠道接入后本类
 * 只用于本地开发与冒烟。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class MockPayChannelAdapter implements PayChannelAdapter {

    private static final String MOCK_TRADE_PREFIX = "MOCK-";

    /** 退款流水号前缀：与支付流水号区分，避免对账时无法判断资金方向 */
    private static final String MOCK_REFUND_PREFIX = "MOCK-RF-";

    private final PayChannelProperties properties;

    @Override
    public String channel() {
        return "MOCK";
    }

    @Override
    public PayChannelResult pay(PayOrderPo order) {
        ensureMockEnabled(order);
        return new PayChannelResult(newMockTradeNo());
    }

    /**
     * 渠道退款：MOCK 直接成功并回填渠道流水号。
     *
     * @param order        支付单
     * @param refundNo     退款单号
     * @param refundAmount 退款金额
     * @return 渠道结果
     */
    @Override
    public PayChannelResult refund(PayOrderPo order, String refundNo, BigDecimal refundAmount) {
        ensureMockEnabled(order);
        return new PayChannelResult(newMockRefundNo());
    }

    /**
     * 渠道状态查询：镜像本地状态。
     *
     * @param order 支付单
     * @return 渠道状态
     */
    @Override
    public String queryStatus(PayOrderPo order) {
        ensureMockEnabled(order);
        return order.getStatus();
    }

    /**
     * 校验 mock 开关与渠道归属。
     *
     * @param order 支付单
     */
    private void ensureMockEnabled(PayOrderPo order) {
        if (!properties.isMock()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.channel.not-integrated",
                    "真实支付渠道尚未接入，请先实现渠道适配器或开启 whale.pay.mock");
        }
        if (!StringUtils.hasText(order.getChannel())
                || !"MOCK".equals(order.getChannel().trim().toUpperCase(Locale.ROOT))) {
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.channel.mock-only",
                    "MOCK 适配器只处理 MOCK 渠道订单: " + order.getChannel(), order.getChannel());
        }
    }

    /**
     * 生成模拟渠道流水号。
     *
     * @return 模拟流水号
     */
    private static String newMockTradeNo() {
        return MOCK_TRADE_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成模拟退款流水号（独立于支付流水号）。
     *
     * @return 模拟退款流水号
     */
    private static String newMockRefundNo() {
        return MOCK_REFUND_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }
}
