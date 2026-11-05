package com.bluewhale.oa.hr.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 请假申请实体，对应表 {@code oa_leave}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_leave")
public class LeaveEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 申请单号 */
    private String code;

    /** 申请人账号 */
    private String applicant;

    /** 申请人用户ID */
    private Long applicantId;

    /** 请假类型 */
    private String leaveType;

    /** 假期类型ID */
    private Long leaveTypeId;

    /** 开始日期 */
    private LocalDate startDate;

    /** 开始时段 */
    private String startPeriod;

    /** 结束日期 */
    private LocalDate endDate;

    /** 结束时段 */
    private String endPeriod;

    /** 工作交接人用户ID */
    private Long handoverUserId;

    /** 审批实例ID */
    private Long approvalInstanceId;

    /** 请假天数 */
    private BigDecimal days;

    /** 请假事由 */
    private String reason;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
