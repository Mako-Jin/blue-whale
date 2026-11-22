package com.bluewhale.pay.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 对账差异视图对象。
 *
 * @author BlueWhale
 */
@Data
public class PayReconcileDiffVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 差异 ID */
    private Long id;

    /** 批次 ID */
    private Long batchId;

    /** 渠道编码 */
    private String channel;

    /** 对账业务日 */
    private LocalDate bizDate;

    /** 支付单号 */
    private String orderNo;

    /** 渠道流水号 */
    private String channelTradeNo;

    /** 差异类型 */
    private String diffType;

    /** 本地状态 */
    private String localStatus;

    /** 渠道状态 */
    private String channelStatus;

    /** 本地金额 */
    private BigDecimal localAmount;

    /** 渠道金额 */
    private BigDecimal channelAmount;

    /** 币种 */
    private String currency;

    /** 处置状态 */
    private String handleStatus;

    /** 处置备注 */
    private String handleRemark;

    /** 处置人 */
    private Long handledBy;

    /** 处置时间 */
    private LocalDateTime handledAt;

    /** 创建时间 */
    private LocalDateTime createTime;
}
