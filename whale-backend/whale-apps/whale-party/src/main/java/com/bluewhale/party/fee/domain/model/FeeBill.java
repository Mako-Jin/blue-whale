package com.bluewhale.party.fee.domain.model;

import com.bluewhale.party.common.PartyErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 党费月度账单。
 *
 * <p>{@link #duesBase} 与 {@link #rate} 都是<b>生成时快照</b>：党员调薪或标准调整后不回改历史账单，
 * 否则去年已缴清的账单会被改成「欠缴」，补缴台账也就失去意义。</p>
 *
 * <p>{@link #periodMonth} 用 {@code yyyyMM} 字符串而不是日期列：党费按<b>月份</b>归属，
 * 用 DATE 存「2026-11-01」会在跨时区与格式化上引入不必要的歧义，
 * 而字符串同时天然可排序、可直接拼进索引。</p>
 *
 * @author BlueWhale
 */
@Data
public class FeeBill {

    /** 备注最大长度 */
    public static final int REMARK_MAX_LENGTH = 255;

    /** 月份文本长度（yyyyMM） */
    public static final int PERIOD_LENGTH = 6;

    /** 主键 */
    private Long id;

    /** 党员档案 ID */
    private Long memberId;

    /** 支部 ID（生成时快照） */
    private Long branchId;

    /** 党员姓名快照 */
    private String memberName;

    /** 所属月份 yyyyMM */
    private String periodMonth;

    /** 党费基数（生成时快照） */
    private BigDecimal duesBase;

    /** 缴费比例（生成时快照） */
    private BigDecimal rate;

    /** 应缴金额 */
    private BigDecimal dueAmount;

    /** 实缴金额（累计） */
    private BigDecimal paidAmount;

    /** 最近缴费日期 */
    private LocalDate paidDate;

    /** 状态 */
    private FeeBillStatus status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 生成账单。
     *
     * <p>刻意只接收基数字值而不是 {@code PartyMember} 对象：党费上下文只关心
     * 「谁的多少钱按哪个比例」这四个事实，传入整个党员档案会让两个上下文在编译期绑死，
     * 后续党员档案加字段时党费模块也要跟着重新编译与回归。</p>
     *
     * @param memberId 党员档案 ID
     * @param branchId 支部 ID
     * @param memberName 党员姓名
     * @param periodMonth 所属月份 yyyyMM
     * @param duesBase 党费基数
     * @param rule 命中的档位
     * @return 未缴账单
     */
    public static FeeBill of(Long memberId, Long branchId, String memberName, String periodMonth,
                            BigDecimal duesBase, FeeRule rule) {
        if (memberId == null || memberId <= 0) {
            throw PartyErrorCode.exception(PartyErrorCode.FEE_INVALID, "党员不能为空");
        }
        if (!isValidPeriod(periodMonth)) {
            throw PartyErrorCode.exception(PartyErrorCode.FEE_INVALID, "所属月份格式应为 yyyyMM");
        }
        if (rule == null) {
            throw PartyErrorCode.exception(PartyErrorCode.FEE_RULE_MISSING);
        }
        FeeBill bill = new FeeBill();
        bill.setMemberId(memberId);
        bill.setBranchId(branchId);
        bill.setMemberName(memberName);
        bill.setPeriodMonth(periodMonth);
        bill.setDuesBase(duesBase == null ? BigDecimal.ZERO : duesBase);
        bill.setRate(rule.getRate());
        bill.setDueAmount(DuesCalculator.dueOf(bill.getDuesBase(), rule));
        bill.setPaidAmount(BigDecimal.ZERO);
        bill.setStatus(FeeBillStatus.UNPAID);
        return bill;
    }

    /**
     * 校验账单字段。
     */
    public void validate() {
        if (!isValidPeriod(periodMonth)) {
            throw PartyErrorCode.exception(PartyErrorCode.FEE_INVALID, "所属月份格式应为 yyyyMM");
        }
        if (dueAmount == null || dueAmount.signum() < 0) {
            throw PartyErrorCode.exception(PartyErrorCode.FEE_INVALID, "应缴金额不能为负");
        }
        if (paidAmount == null) {
            paidAmount = BigDecimal.ZERO;
        }
        if (paidAmount.signum() < 0) {
            throw PartyErrorCode.exception(PartyErrorCode.FEE_INVALID, "实缴金额不能为负");
        }
        if (status == null) {
            status = FeeBillStatus.UNPAID;
        }
        if (remark != null && remark.trim().length() > REMARK_MAX_LENGTH) {
            throw PartyErrorCode.exception(PartyErrorCode.FEE_INVALID,
                    "备注不得超过 " + REMARK_MAX_LENGTH + " 字符");
        }
    }

    /**
     * 登记缴费（可多次缴纳，累计到应缴为止）。
     *
     * @param amount 本次缴纳金额
     * @param payDate 缴费日期（空表示当天）
     */
    public void pay(BigDecimal amount, LocalDate payDate) {
        if (status == FeeBillStatus.WAIVED) {
            throw PartyErrorCode.exception(PartyErrorCode.FEE_INVALID, "该月党费已免缴，无需登记缴费");
        }
        if (amount == null || amount.signum() <= 0) {
            throw PartyErrorCode.exception(PartyErrorCode.FEE_INVALID, "缴纳金额必须大于 0");
        }
        if (status == FeeBillStatus.PAID) {
            throw PartyErrorCode.exception(PartyErrorCode.FEE_INVALID, "该月党费已缴清，如需补缴请先调整账单");
        }
        BigDecimal total = (paidAmount == null ? BigDecimal.ZERO : paidAmount)
                .add(amount).setScale(DuesCalculator.AMOUNT_SCALE, java.math.RoundingMode.HALF_UP);
        if (total.compareTo(dueAmount) > 0) {
            throw PartyErrorCode.exception(PartyErrorCode.FEE_INVALID,
                    "累计缴纳 " + total.toPlainString() + " 超过应缴 " + dueAmount.toPlainString());
        }
        this.paidAmount = total;
        this.paidDate = payDate == null ? LocalDate.now() : payDate;
        this.status = total.compareTo(dueAmount) == 0 ? FeeBillStatus.PAID : FeeBillStatus.PARTIAL;
    }

    /**
     * 标记免缴（困难党员经支部研究同意）。
     *
     * @param reason 免缴原因
     */
    public void waive(String reason) {
        if (status == FeeBillStatus.PAID) {
            throw PartyErrorCode.exception(PartyErrorCode.FEE_INVALID, "已缴清的账单不能改为免缴");
        }
        this.status = FeeBillStatus.WAIVED;
        if (reason != null && !reason.isBlank()) {
            String trimmed = reason.trim();
            this.remark = trimmed.length() > REMARK_MAX_LENGTH ? trimmed.substring(0, REMARK_MAX_LENGTH) : trimmed;
        }
    }

    /**
     * 是否未足额缴纳。
     *
     * @return 欠缴返回 true
     */
    public boolean isOutstanding() {
        return status == FeeBillStatus.UNPAID || status == FeeBillStatus.PARTIAL;
    }

    /**
     * 校验月份格式 yyyyMM 且月份在 01–12。
     *
     * @param period 月份文本
     * @return 合法返回 true
     */
    public static boolean isValidPeriod(String period) {
        if (period == null || period.length() != PERIOD_LENGTH) {
            return false;
        }
        for (int index = 0; index < PERIOD_LENGTH; index++) {
            if (!Character.isDigit(period.charAt(index))) {
                return false;
            }
        }
        int month = Integer.parseInt(period.substring(4));
        int year = Integer.parseInt(period.substring(0, 4));
        return month >= 1 && month <= 12 && year >= 2000;
    }
}
