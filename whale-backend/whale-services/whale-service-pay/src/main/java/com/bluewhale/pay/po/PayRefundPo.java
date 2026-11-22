package com.bluewhale.pay.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单实体，对应表 {@code pay_refund}。
 *
 * <p>一笔支付单可多次退款（模型支持部分退款），累计退款额落在
 * {@code pay_order.refund_amount}；本表记录每次退款的明细与渠道流水。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_refund")
public class PayRefundPo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 退款单号（租户内唯一） */
    private String refundNo;

    /** 支付单 ID */
    private Long payOrderId;

    /** 支付单号（冗余存储） */
    private String payOrderNo;

    /** 本次退款金额 */
    private BigDecimal amount;

    /** 退款原因 */
    private String reason;

    /** 状态：CREATED / SUCCESS / FAILED */
    private String status;

    /** 渠道退款流水号 */
    private String channelRefundNo;

    /** 退款完成时间 */
    private LocalDateTime refundTime;

    /** 备注 */
    private String remark;
}
