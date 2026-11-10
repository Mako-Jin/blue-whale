package com.bluewhale.erp.finance.domain.model;

import com.bluewhale.erp.common.Amounts;
import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 会计凭证聚合。
 *
 * <p>不变量：借贷必须平衡；每条分录只允许单边金额；已记账凭证不可修改，
 * 只能生成红冲凭证。凭证号由调用方按期间分配后写入。</p>
 *
 * @author BlueWhale
 */
@Data
public class Voucher {

    /** 主键 */
    private Long id;

    /** 凭证号 */
    private String voucherNo;

    /** 凭证日期 */
    private LocalDate voucherDate;

    /** 期间编码 */
    private String periodCode;

    /** 凭证类型：MANUAL / REVERSAL */
    private String voucherType;

    /** 状态：DRAFT / POSTED / REVERSED */
    private String status;

    /** 币种 */
    private String currency;

    /** 汇率 */
    private BigDecimal exchangeRate;

    /** 借方合计 */
    private BigDecimal totalDebit;

    /** 贷方合计 */
    private BigDecimal totalCredit;

    /** 来源类型 */
    private String sourceType;

    /** 来源单据 ID */
    private String sourceId;

    /** 红冲的原凭证 ID */
    private Long reversalOfId;

    /** 被哪张红冲凭证冲销 */
    private Long reversedById;

    /** 备注 */
    private String remark;

    /** 记账时间 */
    private LocalDateTime postedAt;

    /** 记账人 */
    private Long postedBy;

    /** 分录 */
    private List<VoucherLine> lines;

    /**
     * 校验并计算合计。
     */
    public void validateAndSum() {
        if (lines == null || lines.isEmpty()) {
            throw ErpErrorCode.exception(ErpErrorCode.VOUCHER_LINE_EMPTY);
        }
        for (VoucherLine line : lines) {
            if (line.getAccountCode() == null || line.getAccountName() == null) {
                throw ErpErrorCode.exception(ErpErrorCode.ACCOUNT_INVALID);
            }
            boolean hasDebit = !Amounts.isZero(line.getDebitAmount());
            boolean hasCredit = !Amounts.isZero(line.getCreditAmount());
            if (hasDebit && hasCredit) {
                throw ErpErrorCode.exception(ErpErrorCode.AMOUNT_INVALID, "同一分录不能同时有借方和贷方金额");
            }
            Amounts.nonNegative(line.getDebitAmount(), "借方金额");
            Amounts.nonNegative(line.getCreditAmount(), "贷方金额");
        }
        BigDecimal debit = Amounts.total(lines.stream().map(VoucherLine::debit).toList());
        BigDecimal credit = Amounts.total(lines.stream().map(VoucherLine::credit).toList());
        if (debit.signum() == 0 && credit.signum() == 0) {
            throw ErpErrorCode.exception(ErpErrorCode.AMOUNT_INVALID, "凭证金额不能全为零");
        }
        if (debit.compareTo(credit) != 0) {
            throw ErpErrorCode.exception(ErpErrorCode.VOUCHER_UNBALANCED,
                    "凭证借贷不平衡：借方 " + debit + "，贷方 " + credit);
        }
        totalDebit = debit;
        totalCredit = credit;
    }

    /**
     * 记账。
     *
     * @param userId 记账人 ID
     * @param now 记账时间
     */
    public void post(Long userId, LocalDateTime now) {
        if (!FinanceStatus.VOUCHER_DRAFT.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.VOUCHER_STATE_CONFLICT, "只有草稿凭证可以记账");
        }
        validateAndSum();
        status = FinanceStatus.VOUCHER_POSTED;
        postedAt = now;
        postedBy = userId;
    }

    /**
     * 标记为已红冲。
     *
     * @param reversalVoucherId 红冲凭证 ID
     */
    public void markReversed(Long reversalVoucherId) {
        if (!FinanceStatus.VOUCHER_POSTED.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.VOUCHER_STATE_CONFLICT, "只有已记账凭证可以红冲");
        }
        status = FinanceStatus.VOUCHER_REVERSED;
        reversedById = reversalVoucherId;
    }

    /**
     * 是否可红冲。
     *
     * @return 已记账且未被红冲返回 true
     */
    public boolean reversible() {
        return FinanceStatus.VOUCHER_POSTED.equals(status);
    }
}
