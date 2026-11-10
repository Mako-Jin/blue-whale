package com.bluewhale.erp.qc.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 量具校准计划实体，对应表 {@code erp_qc_calibration_plan}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_qc_calibration_plan")
public class CalibrationPlanEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 校准计划号 */
    private String planNo;

    /** 量具编号 */
    private String toolNo;

    /** 校准周期（月） */
    private Integer cycleMonths;

    /** 下次校准日期 */
    private LocalDate nextDate;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
