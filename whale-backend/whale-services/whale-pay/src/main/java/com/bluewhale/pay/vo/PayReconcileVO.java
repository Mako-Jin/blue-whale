package com.bluewhale.pay.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 支付单对账结果视图对象
 *
 * @author BlueWhale
 */
@Data
public class PayReconcileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 支付单ID */
    private Long orderId;

    /** 支付单号 */
    private String orderNo;

    /** 本地状态 */
    private String localStatus;

    /** 渠道状态 */
    private String channelStatus;

    /** 本地与渠道状态是否一致 */
    private boolean consistent;

    /** 渠道流水号 */
    private String providerTradeNo;

    /** 对账时间 */
    private LocalDateTime checkedAt;
}
