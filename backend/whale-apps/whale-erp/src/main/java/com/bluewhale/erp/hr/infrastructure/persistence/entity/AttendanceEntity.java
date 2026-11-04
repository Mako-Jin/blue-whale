package com.bluewhale.erp.hr.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 考勤汇总实体，对应表 {@code erp_hr_attendance_summary}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_hr_attendance_summary")
public class AttendanceEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 员工编号 */
    private String employeeNo;

    /** 考勤期间 */
    private String period;

    /** 应出勤天数 */
    private BigDecimal workDays;

    /** 实际出勤天数 */
    private BigDecimal actualDays;

    /** 请假小时数 */
    private BigDecimal leaveHours;

    /** 加班小时数 */
    private BigDecimal overtimeHours;

    /** 缺勤天数 */
    private BigDecimal absentDays;

    /** 备注 */
    private String remark;
}
