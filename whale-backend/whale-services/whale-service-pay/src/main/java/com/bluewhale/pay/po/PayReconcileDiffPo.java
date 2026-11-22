package com.bluewhale.pay.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 对账差异实体，对应表 {@code pay_reconcile_diff}。
 *
 * <p>差异按「批次 + 渠道 + 单号 + 类型」唯一，重复对账不会产生重复差异；
 * 金额不一致禁止自动补偿，必须人工确认并留处置记录。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_reconcile_diff")
public class PayReconcileDiffPo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long tenantId;

    /** 批次 ID */
    private Long batchId;

    /** 渠道编码 */
    private String channel;

    /** 对账业务日 */
    private LocalDate bizDate;

    /** 支付单号（渠道单缺失时为空） */
    private String orderNo;

    /** 渠道流水号 */
    private String channelTradeNo;

    /** 差异类型：LOCAL_ONLY / CHANNEL_ONLY / STATUS_MISMATCH / AMOUNT_MISMATCH */
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

    /** 处置状态：OPEN / CONFIRMED / COMPENSATED / SUSPENDED */
    private String handleStatus;

    /** 处置备注 */
    private String handleRemark;

    /** 处置人用户 ID */
    private Long handledBy;

    /** 处置时间 */
    private LocalDateTime handledAt;
}
