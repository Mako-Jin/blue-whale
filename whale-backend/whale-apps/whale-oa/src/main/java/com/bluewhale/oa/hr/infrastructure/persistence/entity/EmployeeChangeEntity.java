package com.bluewhale.oa.hr.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 人员异动单实体，对应表 {@code oa_employee_change}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_employee_change")
public class EmployeeChangeEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 异动单号 */
    private String changeNo;

    /** 异动类型 */
    private String changeType;

    /** 目标用户ID */
    private Long userId;

    /** 员工姓名 */
    private String employeeName;

    /** 登录账号 */
    private String username;

    /** 原部门ID */
    private Long fromDeptId;

    /** 目标部门ID */
    private Long toDeptId;

    /** 原在职状态 */
    private String fromStatus;

    /** 目标在职状态 */
    private String toStatus;

    /** 生效日期 */
    private LocalDate effectiveDate;

    /** 异动原因 */
    private String reason;

    /** 状态 */
    private String status;

    /** 审批实例ID */
    private Long approvalInstanceId;

    /** 申请人ID */
    private Long applicantId;

    /** 申请人姓名 */
    private String applicantName;

    /** 执行人ID */
    private Long executorId;

    /** 执行人姓名 */
    private String executorName;

    /** 执行时间 */
    private LocalDateTime executedTime;

    /** 执行结果 */
    private String executeResult;

    /** 备注 */
    private String remark;
}
