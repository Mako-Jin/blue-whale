package com.bluewhale.pay.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单实体，对应表 {@code pay_order}。
 *
 * <p>公共字段（id / create_time / update_time / deleted）继承自 {@link BaseEntity}，
 * 其余字段与 {@code db/init.sql} 中的 {@code pay_order} 列逐字段对齐。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_order")
public class PayOrder extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 支付单号（业务唯一） */
    private String orderNo;

    /** 业务标识（外部订单号 / 业务键） */
    private String businessKey;

    /** 业务方类型（MALL_ORDER 等，决定回调路由） */
    private String bizType;

    /** 业务回调地址（格式：&lt;服务名&gt;/&lt;路径&gt;，如 whale-mall/mall/internal/order/paid） */
    private String notifyUrl;

    /** 支付金额 */
    private BigDecimal amount;

    /** 币种，默认 CNY */
    private String currency;

    /** 支付渠道，如 ALIPAY / WECHAT / BALANCE */
    private String channel;

    /** 状态：CREATED 待支付 / PAID 已支付 / CANCELLED 已取消 / REFUNDED 已退款 */
    private String status;

    /** 支付完成时间（未支付为 {@code null}） */
    private LocalDateTime payTime;

    /** 渠道流水号（支付成功后回填；mock 模式为模拟流水号） */
    private String channelTradeNo;

    /** 已退款金额（未退款为 0） */
    private BigDecimal refundAmount;

    /** 退款完成时间（未退款为 {@code null}） */
    private LocalDateTime refundTime;

    /** 备注 */
    private String remark;
}
