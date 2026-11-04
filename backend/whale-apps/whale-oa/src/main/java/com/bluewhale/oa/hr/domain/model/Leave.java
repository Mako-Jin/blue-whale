package com.bluewhale.oa.hr.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 请假申请聚合。
 *
 * <p>仅 {@link LeaveStatus#PENDING} 可编辑、删除与状态迁移；单号在租户内唯一。
 * 假期额度扣减属于审批通过后的业务副作用，由审批上下文幂等执行。</p>
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

    /** 请假类型：ANNUAL / SICK / PERSONAL */
    private String leaveType;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

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

    /**
     * 提交申请：初始化为待审批。
     */
    public void submit() {
        status = LeaveStatus.PENDING;
    }

    /**
     * 校验日期区间。
     */
    public void ensureValidPeriod() {
        if (endDate.isBefore(startDate)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "结束日期不能早于开始日期");
        }
    }

    /**
     * 校验可编辑（含删除）。
     */
    public void ensureEditable() {
        if (status != LeaveStatus.PENDING) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅待审批申请可编辑或删除");
        }
    }

    /**
     * 变更状态。
     *
     * @param target 目标状态
     */
    public void changeStatus(LeaveStatus target) {
        if (status != LeaveStatus.PENDING) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅待审批申请可变更状态");
        }
        status = target;
    }
}
