package com.bluewhale.pay.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 支付/退款流水实体，对应表 {@code pay_transaction}。
 *
 * <p>每次支付或退款成功都会追加一行流水，便于按支付单追溯渠道动作；
 * 流水只追加不修改，失败动作不落流水（由接口日志与应用日志追溯）。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_transaction")
public class PayTransaction extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 支付单ID（逻辑关联 pay_order.id） */
    private Long orderId;

    /** 支付单号（冗余存储，支付单删除后仍可追溯） */
    private String orderNo;

    /** 流水类型：PAY 支付 / REFUND 退款 */
    private String type;

    /** 支付渠道 */
    private String channel;

    /** 本次流水金额 */
    private BigDecimal amount;

    /** 币种 */
    private String currency;

    /** 渠道流水号 */
    private String providerTradeNo;

    /** 流水状态：SUCCESS 成功 / FAILED 失败 */
    private String status;

    /** 失败原因（成功为 {@code null}） */
    private String errorMessage;
}
