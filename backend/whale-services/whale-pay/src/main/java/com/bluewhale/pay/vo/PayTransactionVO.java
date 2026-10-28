package com.bluewhale.pay.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付/退款流水视图对象
 *
 * @author BlueWhale
 */
@Data
public class PayTransactionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 支付单ID */
    private Long orderId;

    /** 支付单号 */
    private String orderNo;

    /** 流水类型：PAY / REFUND */
    private String type;

    /** 支付渠道 */
    private String channel;

    /** 流水金额 */
    private BigDecimal amount;

    /** 币种 */
    private String currency;

    /** 渠道流水号 */
    private String providerTradeNo;

    /** 流水状态：SUCCESS / FAILED */
    private String status;

    /** 失败原因 */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createTime;
}
