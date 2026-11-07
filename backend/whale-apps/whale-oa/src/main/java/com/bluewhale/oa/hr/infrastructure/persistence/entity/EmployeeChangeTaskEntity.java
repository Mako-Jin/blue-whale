package com.bluewhale.oa.hr.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 人员异动任务实体，对应表 {@code oa_employee_change_task}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_employee_change_task")
public class EmployeeChangeTaskEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 异动单ID */
    private Long changeId;

    /** 任务编码 */
    private String taskCode;

    /** 任务名称 */
    private String taskName;

    /** 负责人ID */
    private Long assigneeId;

    /** 负责人姓名 */
    private String assigneeName;

    /** 状态 */
    private String status;

    /** 完成时间 */
    private LocalDateTime doneTime;

    /** 备注 */
    private String remark;
}
