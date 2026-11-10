package com.bluewhale.oa.hr.domain.model;

import com.bluewhale.oa.common.OaErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 出差申请聚合。
 *
 * <p>仅 {@link TripStatus#PENDING} 可编辑、删除与状态迁移；天数由服务端按自然日计算（含首尾）；
 * 预支金额不得超过预算；出差报告仅在审批通过后可提交且只能提交一次。</p>
 *
 * @author BlueWhale
 */
@Data
public class Trip {

    /** 主键 */
    private Long id;

    /** 申请单号 */
    private String code;

    /** 申请人账号 */
    private String applicant;

    /** 申请人用户ID */
    private Long applicantId;

    /** 出差目的地 */
    private String destination;

    /** 出差开始日期 */
    private LocalDate startDate;

    /** 出差结束日期 */
    private LocalDate endDate;

    /** 出差天数（自然日） */
    private Integer days;

    /** 交通方式 */
    private TripTransportType transportType;

    /** 预算金额 */
    private BigDecimal budgetAmount;

    /** 预支金额 */
    private BigDecimal advanceAmount;

    /** 报告状态 */
    private TripReportStatus reportStatus;

    /** 出差报告内容 */
    private String reportContent;

    /** 报告提交时间 */
    private LocalDateTime reportTime;

    /** 状态 */
    private TripStatus status;

    /** 审批实例ID */
    private Long approvalInstanceId;

    /** 备注 */
    private String remark;

    /** 行程列表 */
    private List<TripItinerary> itineraries;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 提交申请：初始化为待审批与待提交报告。
     */
    public void submit() {
        status = TripStatus.PENDING;
        reportStatus = TripReportStatus.PENDING;
    }

    /**
     * 校验出差日期与金额，并计算自然日天数。
     */
    public void ensureValid() {
        if (startDate == null || endDate == null) {
            throw OaErrorCode.exception(OaErrorCode.TRIP_INVALID_PERIOD, "出差起止日期不能为空");
        }
        if (endDate.isBefore(startDate)) {
            throw OaErrorCode.exception(OaErrorCode.TRIP_INVALID_PERIOD, "出差结束日期不能早于开始日期");
        }
        days = (int) (ChronoUnit.DAYS.between(startDate, endDate) + 1);
        BigDecimal budget = budgetAmount == null ? BigDecimal.ZERO : budgetAmount;
        BigDecimal advance = advanceAmount == null ? BigDecimal.ZERO : advanceAmount;
        if (budget.signum() < 0 || advance.signum() < 0) {
            throw OaErrorCode.exception(OaErrorCode.TRIP_INVALID, "预算与预支金额不能为负数");
        }
        if (advance.compareTo(budget) > 0) {
            throw OaErrorCode.exception(OaErrorCode.TRIP_INVALID, "预支金额不能超过预算金额");
        }
        budgetAmount = budget;
        advanceAmount = advance;
    }

    /**
     * 校验可编辑（含删除）。
     */
    public void ensureEditable() {
        if (status != TripStatus.PENDING) {
            throw OaErrorCode.exception(OaErrorCode.TRIP_STATUS_CONFLICT, "仅待审批出差申请可编辑或删除");
        }
    }

    /**
     * 变更状态；重复迁移到同一状态幂等返回。
     *
     * @param target 目标状态
     * @return 实际发生状态迁移返回 {@code true}；重复设置同一状态返回 {@code false}
     */
    public boolean changeStatus(TripStatus target) {
        if (target == null) {
            throw OaErrorCode.exception(OaErrorCode.TRIP_STATUS_CONFLICT, "目标状态不能为空");
        }
        if (status == target) {
            return false;
        }
        if (status != TripStatus.PENDING) {
            throw OaErrorCode.exception(OaErrorCode.TRIP_STATUS_CONFLICT,
                    "申请已结束（" + status + "），不能变更为 " + target);
        }
        status = target;
        return true;
    }

    /**
     * 提交出差报告：仅审批通过且报告未提交时可提交。
     *
     * @param content 报告内容
     * @param now 提交时间
     */
    public void submitReport(String content, LocalDateTime now) {
        if (status != TripStatus.APPROVED) {
            throw OaErrorCode.exception(OaErrorCode.TRIP_REPORT_NOT_ALLOWED, "仅审批通过的出差申请可提交报告");
        }
        if (reportStatus == TripReportStatus.SUBMITTED) {
            throw OaErrorCode.exception(OaErrorCode.TRIP_REPORT_NOT_ALLOWED, "出差报告已提交，不能重复提交");
        }
        reportStatus = TripReportStatus.SUBMITTED;
        reportContent = content;
        reportTime = now;
    }
}
