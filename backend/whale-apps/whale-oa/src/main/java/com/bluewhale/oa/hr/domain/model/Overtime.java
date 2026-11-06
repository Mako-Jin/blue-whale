package com.bluewhale.oa.hr.domain.model;

import com.bluewhale.oa.common.OaErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 加班申请聚合。
 *
 * <p>仅 {@link OvertimeStatus#PENDING} 可编辑、删除与状态迁移；单号在租户内唯一。
 * 加班时长由应用服务按起止时间计算，客户端传入的时长不参与落库。</p>
 *
 * @author BlueWhale
 */
@Data
public class Overtime {

    /** 主键 */
    private Long id;

    /** 申请单号 */
    private String code;

    /** 申请人账号 */
    private String applicant;

    /** 申请人用户ID */
    private Long applicantId;

    /** 加班日期 */
    private LocalDate overtimeDate;

    /** 加班开始时间 */
    private LocalTime startTime;

    /** 加班结束时间 */
    private LocalTime endTime;

    /** 加班时长（小时） */
    private BigDecimal hours;

    /** 补偿方式 */
    private OvertimeCompensation compensation;

    /** 关联项目名称 */
    private String projectName;

    /** 加班事由 */
    private String reason;

    /** 状态 */
    private OvertimeStatus status;

    /** 审批实例ID */
    private Long approvalInstanceId;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 提交申请：初始化为待审批。
     */
    public void submit() {
        status = OvertimeStatus.PENDING;
    }

    /**
     * 校验加班日期与起止时间。
     *
     * <p>日期不能为空；结束时间必须晚于开始时间（不支持跨天加班）。</p>
     */
    public void ensureValidPeriod() {
        if (overtimeDate == null) {
            throw OaErrorCode.exception(OaErrorCode.OVERTIME_INVALID, "加班日期不能为空");
        }
        if (startTime == null || endTime == null) {
            throw OaErrorCode.exception(OaErrorCode.OVERTIME_INVALID, "加班起止时间不能为空");
        }
        if (!endTime.isAfter(startTime)) {
            throw OaErrorCode.exception(OaErrorCode.OVERTIME_INVALID, "加班结束时间必须晚于开始时间");
        }
    }

    /**
     * 校验可编辑（含删除）。
     */
    public void ensureEditable() {
        if (status != OvertimeStatus.PENDING) {
            throw OaErrorCode.exception(OaErrorCode.OVERTIME_STATUS_CONFLICT, "仅待审批申请可编辑或删除");
        }
    }

    /**
     * 变更状态；重复迁移到同一状态幂等返回。
     *
     * @param target 目标状态
     * @return 实际发生状态迁移返回 {@code true}；重复设置同一状态返回 {@code false}
     */
    public boolean changeStatus(OvertimeStatus target) {
        if (target == null) {
            throw OaErrorCode.exception(OaErrorCode.OVERTIME_STATUS_CONFLICT, "目标状态不能为空");
        }
        if (status == target) {
            return false;
        }
        if (status != OvertimeStatus.PENDING) {
            throw OaErrorCode.exception(OaErrorCode.OVERTIME_STATUS_CONFLICT,
                    "申请已结束（" + status + "），不能变更为 " + target);
        }
        status = target;
        return true;
    }
}
