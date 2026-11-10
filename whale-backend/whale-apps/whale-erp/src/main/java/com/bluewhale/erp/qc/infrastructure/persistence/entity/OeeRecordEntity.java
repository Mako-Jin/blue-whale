package com.bluewhale.erp.qc.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 设备 OEE 记录实体，对应表 {@code erp_qc_oee_record}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_qc_oee_record")
public class OeeRecordEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 设备编码 */
    private String equipmentNo;

    /** 统计日期 */
    private LocalDate recordDate;

    /** 计划运行分钟 */
    private BigDecimal plannedMinutes;

    /** 实际运行分钟 */
    private BigDecimal runMinutes;

    /** 停机分钟 */
    private BigDecimal downMinutes;

    /** 理论产量 */
    private BigDecimal idealQty;

    /** 实际产量 */
    private BigDecimal outputQty;

    /** 合格产量 */
    private BigDecimal goodQty;

    /** 时间开动率 */
    private BigDecimal availabilityRate;

    /** 性能开动率 */
    private BigDecimal performanceRate;

    /** 合格率 */
    private BigDecimal qualityRate;

    /** OEE */
    private BigDecimal oeeRate;
}
