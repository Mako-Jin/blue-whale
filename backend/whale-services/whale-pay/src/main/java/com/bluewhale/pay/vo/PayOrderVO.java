package com.bluewhale.pay.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单视图对象（字段与 {@code pay_order} 表一致）
 *
 * @author BlueWhale
 */
@Data
public class PayOrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 支付单号 */
    private String orderNo;

    /** 业务标识 */
    private String businessKey;

    /** 支付金额 */
    private BigDecimal amount;

    /** 币种 */
    private String currency;

    /** 支付渠道 */
    private String channel;

    /** 状态：CREATED 待支付 / PAID 已支付 / CANCELLED 已取消 */
    private String status;

    /** 支付完成时间 */
    private LocalDateTime payTime;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
