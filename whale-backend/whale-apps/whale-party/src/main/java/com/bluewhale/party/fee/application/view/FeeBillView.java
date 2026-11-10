package com.bluewhale.party.fee.application.view;

import com.bluewhale.party.fee.domain.model.FeeBill;
import com.bluewhale.party.fee.domain.model.FeeBillStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 党费账单视图。
 *
 * @author BlueWhale
 */
@Data
public class FeeBillView {

    /** 账单 ID */
    private Long id;

    /** 党员档案 ID */
    private Long memberId;

    /** 党员姓名 */
    private String memberName;

    /** 支部 ID */
    private Long branchId;

    /** 支部名称 */
    private String branchName;

    /** 所属月份 yyyyMM */
    private String periodMonth;

    /** 党费基数 */
    private BigDecimal duesBase;

    /** 缴费比例 */
    private BigDecimal rate;

    /** 应缴金额 */
    private BigDecimal dueAmount;

    /** 实缴金额 */
    private BigDecimal paidAmount;

    /** 欠缴金额（应缴 - 实缴，免缴为 0） */
    private BigDecimal outstandingAmount;

    /** 最近缴费日期 */
    private LocalDate paidDate;

    /** 状态码 */
    private String status;

    /** 状态标签 */
    private String statusLabel;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 由领域模型构建视图。
     *
     * @param bill 账单
     * @return 视图
     */
    public static FeeBillView from(FeeBill bill) {
        FeeBillView view = new FeeBillView();
        view.setId(bill.getId());
        view.setMemberId(bill.getMemberId());
        view.setMemberName(bill.getMemberName());
        view.setBranchId(bill.getBranchId());
        view.setPeriodMonth(bill.getPeriodMonth());
        view.setDuesBase(bill.getDuesBase());
        view.setRate(bill.getRate());
        view.setDueAmount(bill.getDueAmount());
        view.setPaidAmount(bill.getPaidAmount());
        view.setPaidDate(bill.getPaidDate());
        view.setRemark(bill.getRemark());
        view.setCreateTime(bill.getCreateTime());
        FeeBillStatus status = bill.getStatus() == null ? FeeBillStatus.UNPAID : bill.getStatus();
        view.setStatus(status.name());
        view.setStatusLabel(status.getLabel());
        view.setOutstandingAmount(outstandingOf(bill, status));
        return view;
    }

    /**
     * 计算欠缴金额。
     *
     * @param bill 账单
     * @param status 账单状态
     * @return 欠缴金额
     */
    private static BigDecimal outstandingOf(FeeBill bill, FeeBillStatus status) {
        if (status == FeeBillStatus.WAIVED) {
            return BigDecimal.ZERO;
        }
        BigDecimal due = bill.getDueAmount() == null ? BigDecimal.ZERO : bill.getDueAmount();
        BigDecimal paid = bill.getPaidAmount() == null ? BigDecimal.ZERO : bill.getPaidAmount();
        BigDecimal gap = due.subtract(paid);
        return gap.signum() <= 0 ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : gap;
    }
}
