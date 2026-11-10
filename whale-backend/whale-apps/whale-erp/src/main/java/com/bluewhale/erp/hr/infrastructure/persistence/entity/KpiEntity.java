package com.bluewhale.erp.hr.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 绩效指标实体，对应表 {@code erp_hr_kpi}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_hr_kpi")
public class KpiEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 绩效周期主键 */
    private Long cycleId;

    /** 指标名称 */
    private String name;

    /** 权重 */
    private BigDecimal weight;

    /** 目标值 */
    private BigDecimal targetValue;

    /** 计量单位 */
    private String unit;

    /** 备注 */
    private String remark;
}
