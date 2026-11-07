package com.bluewhale.pay.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单 VO。
 *
 * @author BlueWhale
 */
@Data
public class PayRefundVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 退款单 ID */
    private Long id;

    /** 退款单号 */
    private String refundNo;

    /** 支付单 ID */
    private Long payOrderId;

    /** 支付单号 */
    private String payOrderNo;

    /** 本次退款金额 */
    private BigDecimal amount;

    /** 退款原因 */
    private String reason;

    /** 状态 */
    private String status;

    /** 渠道退款流水号 */
    private String channelRefundNo;

    /** 退款完成时间 */
    private LocalDateTime refundTime;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
