package com.bluewhale.erp.hr.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 绩效考核结果实体，对应表 {@code erp_hr_appraisal}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_hr_appraisal")
public class AppraisalEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 绩效周期主键 */
    private Long cycleId;

    /** 绩效指标主键 */
    private Long kpiId;

    /** 指标名称快照 */
    private String kpiName;

    /** 员工编号 */
    private String employeeNo;

    /** 员工姓名快照 */
    private String employeeName;

    /** 得分 */
    private BigDecimal score;

    /** 等级 */
    private String grade;

    /** 评语 */
    private String remark;
}
