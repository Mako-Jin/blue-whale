package com.bluewhale.erp.qc.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 设备保养计划实体，对应表 {@code erp_qc_maintenance_plan}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_qc_maintenance_plan")
public class MaintenancePlanEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 保养计划号 */
    private String planNo;

    /** 设备编码 */
    private String equipmentNo;

    /** 保养周期（天） */
    private Integer cycleDays;

    /** 上次保养日期 */
    private LocalDate lastDate;

    /** 下次保养日期 */
    private LocalDate nextDate;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
