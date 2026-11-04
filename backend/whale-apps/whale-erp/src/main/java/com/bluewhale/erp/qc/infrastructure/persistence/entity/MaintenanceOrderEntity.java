package com.bluewhale.erp.qc.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 设备保养工单实体，对应表 {@code erp_qc_maintenance_order}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_qc_maintenance_order")
public class MaintenanceOrderEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 保养工单号 */
    private String orderNo;

    /** 设备编码 */
    private String equipmentNo;

    /** 保养计划主键 */
    private Long planId;

    /** 类型 */
    private String maintenanceType;

    /** 状态 */
    private String status;

    /** 计划保养日期 */
    private LocalDate planDate;

    /** 实际完成日期 */
    private LocalDate completeDate;

    /** 保养费用 */
    private BigDecimal costAmount;

    /** 保养结果 */
    private String result;

    /** 备注 */
    private String remark;
}
