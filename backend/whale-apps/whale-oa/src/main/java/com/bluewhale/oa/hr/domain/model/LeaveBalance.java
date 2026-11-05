package com.bluewhale.oa.hr.domain.model;

import com.bluewhale.oa.common.OaErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 年度假期额度聚合。
 *
 * <p>不变量：默认 {@code used + frozen ≤ total}；只有假期类型允许负额度时，
 * 冻结可突破总额度。冻结在提交申请时发生，审批通过后先释放冻结再累加已使用，
 * 驳回或撤销仅释放冻结；所有变更由应用服务写入额度流水。</p>
 *
 * @author BlueWhale
 */
@Data
public class LeaveBalance {

    /** 主键 */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 假期类型ID */
    private Long leaveTypeId;

    /** 额度年份 */
    private Integer balanceYear;

    /** 总额度 */
    private BigDecimal totalDays;

    /** 已使用 */
    private BigDecimal usedDays;

    /** 冻结中 */
    private BigDecimal frozenDays;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 可用额度（总额度 - 已使用 - 冻结）。
     *
     * @return 可用额度
     */
    public BigDecimal availableDays() {
        return total().subtract(used()).subtract(frozen());
    }

    /**
     * 冻结额度（提交申请）。
     *
     * @param days 冻结天数，必须为正数
     * @param allowNegative 假期类型是否允许负额度
     */
    public void freeze(BigDecimal days, boolean allowNegative) {
        BigDecimal amount = requirePositive(days);
        if (!allowNegative && used().add(frozen()).add(amount).compareTo(total()) > 0) {
            throw OaErrorCode.exception(OaErrorCode.LEAVE_BALANCE_INSUFFICIENT,
                    "假期额度不足，可用 " + availableDays().toPlainString() + " 天");
        }
        frozenDays = frozen().add(amount);
    }

    /**
     * 释放冻结额度（驳回或撤销）。
     *
     * @param days 释放天数，必须为正数且不超过当前冻结
     */
    public void release(BigDecimal days) {
        BigDecimal amount = requirePositive(days);
        if (frozen().compareTo(amount) < 0) {
            throw OaErrorCode.exception(OaErrorCode.LEAVE_BALANCE_INVALID,
                    "释放冻结天数不能超过当前冻结 " + frozen().toPlainString() + " 天");
        }
        frozenDays = frozen().subtract(amount);
    }

    /**
     * 扣减额度（审批通过）：先释放对应冻结，再累加已使用。
     *
     * @param days 扣减天数，必须为正数
     */
    public void deduct(BigDecimal days) {
        BigDecimal amount = requirePositive(days);
        release(amount);
        usedDays = used().add(amount);
    }

    /**
     * 调整总额度。
     *
     * @param total 新的总额度，不得小于已使用 + 冻结
     */
    public void adjust(BigDecimal total) {
        if (total == null || total.signum() < 0) {
            throw OaErrorCode.exception(OaErrorCode.LEAVE_BALANCE_INVALID, "总额度不能为负数");
        }
        if (used().add(frozen()).compareTo(total) > 0) {
            throw OaErrorCode.exception(OaErrorCode.LEAVE_BALANCE_INVALID,
                    "总额度不能小于已使用 + 冻结（" + used().add(frozen()).toPlainString() + " 天）");
        }
        totalDays = total;
    }

    /**
     * 校验并返回正数天数。
     *
     * @param days 天数
     * @return 正数天数
     */
    private BigDecimal requirePositive(BigDecimal days) {
        if (days == null || days.signum() <= 0) {
            throw OaErrorCode.exception(OaErrorCode.LEAVE_BALANCE_INVALID, "额度变动天数必须大于 0");
        }
        return days;
    }

    /**
     * 总额度（空值按 0 处理）。
     *
     * @return 总额度
     */
    private BigDecimal total() {
        return totalDays == null ? BigDecimal.ZERO : totalDays;
    }

    /**
     * 已使用（空值按 0 处理）。
     *
     * @return 已使用
     */
    private BigDecimal used() {
        return usedDays == null ? BigDecimal.ZERO : usedDays;
    }

    /**
     * 冻结（空值按 0 处理）。
     *
     * @return 冻结
     */
    private BigDecimal frozen() {
        return frozenDays == null ? BigDecimal.ZERO : frozenDays;
    }
}
