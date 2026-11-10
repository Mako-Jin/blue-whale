package com.bluewhale.erp.finance.application.command;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 自动记账命令：由采购收货、销售发货等业务动作触发。
 *
 * @author BlueWhale
 */
@Data
public class AutoVoucherCommand {

    /** 来源类型（GOODS_RECEIPT / SHIPMENT） */
    private String sourceType;

    /** 来源单据号（幂等键） */
    private String sourceId;

    /** 凭证日期 */
    private LocalDate voucherDate;

    /** 币种 */
    private String currency;

    /** 交易金额（应收/应付/收入） */
    private BigDecimal amount;

    /** 成本金额（发货结转成本，可为空） */
    private BigDecimal costAmount;

    /** 实发金额（薪资发放，可为空） */
    private BigDecimal netAmount;

    /** 代扣个税（薪资发放，可为空） */
    private BigDecimal taxAmount;

    /** 代扣社保与公积金（薪资发放，可为空） */
    private BigDecimal withholdingAmount;

    /** 操作人 ID */
    private Long operatorId;

    /** 备注 */
    private String remark;
}
