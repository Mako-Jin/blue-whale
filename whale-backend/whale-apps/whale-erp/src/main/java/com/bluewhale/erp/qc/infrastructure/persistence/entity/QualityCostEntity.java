package com.bluewhale.erp.qc.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 质量成本实体，对应表 {@code erp_qc_quality_cost}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_qc_quality_cost")
public class QualityCostEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 质量成本单号 */
    private String costNo;

    /** 来源类型 */
    private String sourceType;

    /** 来源单据号 */
    private String sourceNo;

    /** 成本类型 */
    private String costType;

    /** 金额 */
    private BigDecimal amount;

    /** 成本日期 */
    private LocalDate costDate;

    /** 备注 */
    private String remark;
}
