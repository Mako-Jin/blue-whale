package com.bluewhale.erp.finance.domain.model;

import com.bluewhale.erp.common.Amounts;
import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 应付台账聚合。
 *
 * <p>不变量：核销（付款）金额不超过应付金额；已结清后不再允许付款。</p>
 *
 * @author BlueWhale
 */
@Data
public class Payable {

    /** 主键 */
    private Long id;

    /** 应付单号 */
    private String docNo;

    /** 供应商 ID */
    private Long supplierId;

    /** 供应商名称快照 */
    private String supplierName;

    /** 来源类型 */
    private String sourceType;

    /** 来源单据 ID */
    private String sourceId;

    /** 单据日期 */
    private LocalDate docDate;

    /** 到期日 */
    private LocalDate dueDate;

    /** 币种 */
    private String currency;

    /** 应付金额 */
    private BigDecimal amount;

    /** 已核销金额 */
    private BigDecimal settledAmount;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /**
     * 未结余额（应付金额 − 已核销）。
     *
     * @return 未结余额
     */
    public BigDecimal remaining() {
        BigDecimal settled = settledAmount == null ? BigDecimal.ZERO : settledAmount;
        return amount.subtract(settled);
    }

    /**
     * 核销付款。
     *
     * @param settleAmount 本次付款金额
     */
    public void settle(BigDecimal settleAmount) {
        if (FinanceStatus.LEDGER_SETTLED.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.LEDGER_CLOSED);
        }
        Amounts.requirePositive(settleAmount, "付款金额");
        BigDecimal remaining = remaining();
        if (settleAmount.compareTo(remaining) > 0) {
            throw ErpErrorCode.exception(ErpErrorCode.SETTLE_EXCEEDED,
                    "付款金额 " + settleAmount + " 超过未结余额 " + remaining);
        }
        settledAmount = (settledAmount == null ? BigDecimal.ZERO : settledAmount).add(settleAmount);
        status = remaining().signum() == 0 ? FinanceStatus.LEDGER_SETTLED : FinanceStatus.LEDGER_PARTIALLY_SETTLED;
    }

    /**
     * 退货冲减应付（仅冲减未核销部分，不生成负数单据）。
     *
     * @param offsetAmount 本次冲减金额
     */
    public void offsetReturn(BigDecimal offsetAmount) {
        if (FinanceStatus.LEDGER_SETTLED.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.LEDGER_CLOSED);
        }
        Amounts.requirePositive(offsetAmount, "退货冲减金额");
        BigDecimal remaining = remaining();
        if (offsetAmount.compareTo(remaining) > 0) {
            throw ErpErrorCode.exception(ErpErrorCode.RETURN_OFFSET_EXCEEDED,
                    "冲减金额 " + offsetAmount + " 超过未结余额 " + remaining);
        }
        amount = amount.subtract(offsetAmount);
        refreshStatus();
    }

    /**
     * 按未结余额刷新状态。
     */
    private void refreshStatus() {
        BigDecimal remaining = remaining();
        if (remaining.signum() == 0) {
            status = FinanceStatus.LEDGER_SETTLED;
        } else if (settledAmount != null && settledAmount.signum() > 0) {
            status = FinanceStatus.LEDGER_PARTIALLY_SETTLED;
        } else {
            status = FinanceStatus.LEDGER_OPEN;
        }
    }

    /**
     * 逾期天数（以今天为基准）。
     *
     * @param today 今天
     * @return 逾期天数，未到期为负数
     */
    public long overdueDays(LocalDate today) {
        return ChronoUnit.DAYS.between(dueDate, today);
    }
}
