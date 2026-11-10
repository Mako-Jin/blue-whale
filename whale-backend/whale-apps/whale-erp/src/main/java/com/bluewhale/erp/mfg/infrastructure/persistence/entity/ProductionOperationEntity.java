package com.bluewhale.erp.mfg.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产订单工序实体，对应表 {@code erp_mfg_production_operation}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_mfg_production_operation")
public class ProductionOperationEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 生产订单 ID */
    private Long orderId;

    /** 工序序号 */
    private Integer stepNo;

    /** 工序名称 */
    private String name;

    /** 工作中心编码 */
    private String workCenterCode;

    /** 单件标准工时 */
    private BigDecimal standardHours;

    /** 准备工时 */
    private BigDecimal setupHours;

    /** 计划数量 */
    private BigDecimal plannedQuantity;

    /** 累计合格数量 */
    private BigDecimal qualifiedQuantity;

    /** 累计报废数量 */
    private BigDecimal scrappedQuantity;

    /** 状态 */
    private String status;

    /** 首次报工时间 */
    private LocalDateTime firstReportAt;

    /** 最近报工时间 */
    private LocalDateTime lastReportAt;
}
