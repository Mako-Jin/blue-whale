package com.bluewhale.oa.hr.domain.model;

import com.bluewhale.oa.common.OaErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 请假申请聚合。
 *
 * <p>仅 {@link LeaveStatus#PENDING} 可编辑、删除与状态迁移；单号在租户内唯一。
 * 假期额度冻结/扣减/释放属于业务副作用，由应用服务在状态迁移时调用额度服务完成。</p>
 *
 * @author BlueWhale
 */
@Data
public class Leave {

    /** 主键 */
    private Long id;

    /** 申请单号 */
    private String code;

    /** 申请人账号 */
    private String applicant;

    /** 申请人用户ID */
    private Long applicantId;

    /** 请假类型：ANNUAL / SICK / PERSONAL */
    private String leaveType;

    /** 假期类型ID */
    private Long leaveTypeId;

    /** 开始日期 */
    private LocalDate startDate;

    /** 开始时段：AM 上午 / PM 下午 */
    private String startPeriod = PERIOD_AM;

    /** 结束日期 */
    private LocalDate endDate;

    /** 结束时段：AM 上午 / PM 下午 */
    private String endPeriod = PERIOD_PM;

    /** 工作交接人用户ID */
    private Long handoverUserId;

    /** 审批实例ID */
    private Long approvalInstanceId;

    /** 请假天数（0.5 步进） */
    private BigDecimal days;

    /** 请假事由 */
    private String reason;

    /** 状态 */
    private LeaveStatus status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 上午时段 */
    public static final String PERIOD_AM = "AM";

    /** 下午时段 */
    public static final String PERIOD_PM = "PM";

    /**
     * 提交申请：初始化为待审批。
     */
    public void submit() {
        status = LeaveStatus.PENDING;
    }

    /**
     * 校验日期与时段区间。
     *
     * <p>结束日期不得早于开始日期；同一天时结束时段不得早于开始时段（PM → AM 非法）。</p>
     */
    public void ensureValidPeriod() {
        if (startDate == null || endDate == null) {
            throw OaErrorCode.exception(OaErrorCode.LEAVE_INVALID, "开始日期与结束日期不能为空");
        }
        ensurePeriod(startPeriod, "开始时段");
        ensurePeriod(endPeriod, "结束时段");
        if (endDate.isBefore(startDate)) {
            throw OaErrorCode.exception(OaErrorCode.LEAVE_INVALID, "结束日期不能早于开始日期");
        }
        if (startDate.equals(endDate) && PERIOD_PM.equals(startPeriod) && PERIOD_AM.equals(endPeriod)) {
            throw OaErrorCode.exception(OaErrorCode.LEAVE_INVALID, "同一天的结束时段不能早于开始时段");
        }
    }

    /**
     * 校验可编辑（含删除）。
     */
    public void ensureEditable() {
        if (status != LeaveStatus.PENDING) {
            throw OaErrorCode.exception(OaErrorCode.LEAVE_STATUS_CONFLICT, "仅待审批申请可编辑或删除");
        }
    }

    /**
     * 变更状态；重复迁移到同一状态幂等返回。
     *
     * @param target 目标状态
     * @return 实际发生状态迁移返回 {@code true}；重复设置同一状态返回 {@code false}
     */
    public boolean changeStatus(LeaveStatus target) {
        if (target == null) {
            throw OaErrorCode.exception(OaErrorCode.LEAVE_STATUS_CONFLICT, "目标状态不能为空");
        }
        if (status == target) {
            return false;
        }
        if (status != LeaveStatus.PENDING) {
            throw OaErrorCode.exception(OaErrorCode.LEAVE_STATUS_CONFLICT,
                    "申请已结束（" + status + "），不能变更为 " + target);
        }
        status = target;
        return true;
    }

    /**
     * 校验时段取值。
     *
     * @param period 时段
     * @param label 字段中文名
     */
    private void ensurePeriod(String period, String label) {
        if (!PERIOD_AM.equals(period) && !PERIOD_PM.equals(period)) {
            throw OaErrorCode.exception(OaErrorCode.LEAVE_INVALID, label + "仅支持 AM / PM");
        }
    }
}
