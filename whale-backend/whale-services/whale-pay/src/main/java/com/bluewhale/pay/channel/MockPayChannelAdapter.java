package com.bluewhale.pay.channel;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pay.config.PayChannelProperties;
import com.bluewhale.pay.enums.PayCallbackEvent;
import com.bluewhale.pay.po.PayOrderPo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
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

    private final ObjectMapper objectMapper;

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
     * 解析回调报文：MOCK 使用 JSON，字段与 {@link ChannelCallbackRequest} 对齐。
     *
     * <p>真实渠道必须在各自的适配器内完成验签；MOCK 只用于本地开发与冒烟，
     * 因此不做签名校验，但仍要求报文结构合法。</p>
     *
     * @param rawBody 原始报文
     * @param headers 原始请求头
     * @return 统一回调请求
     */
    @Override
    public ChannelCallbackRequest parseCallback(String rawBody, Map<String, String> headers) {
        ensureMockEnabled();
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody == null ? "" : rawBody);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.callback.payload-invalid",
                    "渠道回调报文非法", ex.getOriginalMessage());
        }
        if (root == null || !root.isObject()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.callback.payload-invalid", "渠道回调报文非法");
        }
        return new ChannelCallbackRequest(channel(), text(root, "channelTradeNo"),
                text(root, "outTradeNo"), text(root, "refundNo"),
                PayCallbackEvent.fromCode(text(root, "event")), decimal(root, "amount"),
                text(root, "currency"), rawBody == null ? "" : rawBody,
                headers == null ? Map.of() : Map.copyOf(headers));
    }

    /**
     * 校验 mock 开关与渠道归属。
     *
     * @param order 支付单
     */
    private void ensureMockEnabled(PayOrderPo order) {
        ensureMockEnabled();
        if (!StringUtils.hasText(order.getChannel())
                || !"MOCK".equals(order.getChannel().trim().toUpperCase(Locale.ROOT))) {
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.channel.mock-only",
                    "MOCK 适配器只处理 MOCK 渠道订单: " + order.getChannel(), order.getChannel());
        }
    }

    /**
     * 校验 mock 开关。
     */
    private void ensureMockEnabled() {
        if (!properties.isMock()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.channel.not-integrated",
                    "真实支付渠道尚未接入，请先实现渠道适配器或开启 whale.pay.mock");
        }
    }

    /**
     * 读取文本字段，缺失返回 {@code null}。
     *
     * @param root  报文根节点
     * @param field 字段名
     * @return 字段值
     */
    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    /**
     * 读取金额字段，缺失返回 {@code null}，格式非法抛业务异常。
     *
     * @param root  报文根节点
     * @param field 字段名
     * @return 金额
     */
    private static BigDecimal decimal(JsonNode root, String field) {
        String value = text(root, field);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.callback.payload-invalid",
                    "渠道回调金额非法: " + value, value);
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
