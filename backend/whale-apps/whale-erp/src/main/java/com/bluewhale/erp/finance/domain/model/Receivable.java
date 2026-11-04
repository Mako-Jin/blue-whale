package com.bluewhale.erp.finance.domain.model;

import com.bluewhale.erp.common.Amounts;
import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 应收台账聚合。
 *
 * <p>不变量：核销金额与坏账金额之和不超过应收金额；已结清或已核销坏账后
 * 不再允许核销。来源单据由 SCM 后续接入，本波支持手工录入。</p>
 *
 * @author BlueWhale
 */
@Data
public class Receivable {

    /** 主键 */
    private Long id;

    /** 应收单号 */
    private String docNo;

    /** 客户 ID */
    private Long customerId;

    /** 客户名称快照 */
    private String customerName;

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

    /** 应收金额 */
    private BigDecimal amount;

    /** 已核销金额 */
    private BigDecimal settledAmount;

    /** 已计提坏账金额 */
    private BigDecimal badDebtAmount;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /**
     * 未结余额（应收金额 − 已核销 − 已计提坏账）。
     *
     * @return 未结余额
     */
    public BigDecimal remaining() {
        BigDecimal settled = settledAmount == null ? BigDecimal.ZERO : settledAmount;
        BigDecimal badDebt = badDebtAmount == null ? BigDecimal.ZERO : badDebtAmount;
        return amount.subtract(settled).subtract(badDebt);
    }

    /**
     * 核销回款。
     *
     * @param settleAmount 本次核销金额
     */
    public void settle(BigDecimal settleAmount) {
        if (FinanceStatus.LEDGER_SETTLED.equals(status) || FinanceStatus.LEDGER_WRITTEN_OFF.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.LEDGER_CLOSED);
        }
        Amounts.requirePositive(settleAmount, "核销金额");
        BigDecimal remaining = remaining();
        if (settleAmount.compareTo(remaining) > 0) {
            throw ErpErrorCode.exception(ErpErrorCode.SETTLE_EXCEEDED,
                    "核销金额 " + settleAmount + " 超过未结余额 " + remaining);
        }
        settledAmount = (settledAmount == null ? BigDecimal.ZERO : settledAmount).add(settleAmount);
        refreshStatus();
    }

    /**
     * 计提坏账。
     *
     * @param badDebt 本次计提金额
     */
    public void provisionBadDebt(BigDecimal badDebt) {
        if (FinanceStatus.LEDGER_SETTLED.equals(status) || FinanceStatus.LEDGER_WRITTEN_OFF.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.LEDGER_CLOSED);
        }
        Amounts.requirePositive(badDebt, "坏账计提金额");
        BigDecimal remaining = remaining();
        if (badDebt.compareTo(remaining) > 0) {
            throw ErpErrorCode.exception(ErpErrorCode.BAD_DEBT_EXCEEDED,
                    "坏账计提金额 " + badDebt + " 超过未结余额 " + remaining);
        }
        badDebtAmount = (badDebtAmount == null ? BigDecimal.ZERO : badDebtAmount).add(badDebt);
        refreshStatus();
    }

    /**
     * 按未结余额刷新状态。
     */
    private void refreshStatus() {
        BigDecimal remaining = remaining();
        if (remaining.signum() == 0) {
            BigDecimal badDebt = badDebtAmount == null ? BigDecimal.ZERO : badDebtAmount;
            status = badDebt.signum() > 0 ? FinanceStatus.LEDGER_WRITTEN_OFF : FinanceStatus.LEDGER_SETTLED;
        } else if (settledAmount != null && settledAmount.signum() > 0) {
            status = FinanceStatus.LEDGER_PARTIALLY_SETTLED;
        } else {
            status = FinanceStatus.LEDGER_OPEN;
        }
    }

    /**
     * 返回当前台账的账龄天数（以今天为基准）。
     *
     * @param today 今天
     * @return 逾期天数，未到期为负数
     */
    public long overdueDays(LocalDate today) {
        return java.time.temporal.ChronoUnit.DAYS.between(dueDate, today);
    }

    /**
     * 支持按状态过滤的查询辅助。
     *
     * @param statuses 允许的状态
     * @return 当前状态是否在允许集合内
     */
    public boolean statusIn(List<String> statuses) {
        return statuses == null || statuses.isEmpty() || statuses.contains(status);
    }
}
