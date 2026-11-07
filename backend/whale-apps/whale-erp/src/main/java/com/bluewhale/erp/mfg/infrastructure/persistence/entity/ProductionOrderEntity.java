package com.bluewhale.erp.mfg.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生产订单头实体，对应表 {@code erp_mfg_production_order}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_mfg_production_order")
public class ProductionOrderEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 生产订单号 */
    private String orderNo;

    /** 产出物料 ID */
    private Long itemId;

    /** 产出物料编码 */
    private String itemCode;

    /** 产出物料名称 */
    private String itemName;

    /** 计划生产数量 */
    private BigDecimal plannedQuantity;

    /** 已完工数量 */
    private BigDecimal completedQuantity;

    /** 已入库成本 */
    private BigDecimal completedCost;

    /** 累计领料成本 */
    private BigDecimal totalMaterialCost;

    /** 领料仓库 ID */
    private Long issueWarehouseId;

    /** 领料仓库名称 */
    private String issueWarehouseName;

    /** 完工入库仓库 ID */
    private Long targetWarehouseId;

    /** 完工入库仓库名称 */
    private String targetWarehouseName;

    /** 计划开工日期 */
    private LocalDate planStartDate;

    /** 计划完工日期 */
    private LocalDate planEndDate;

    /** 实际完工日期 */
    private LocalDate completeDate;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 工艺路线编号快照 */
    private String routingNo;

    /** 工艺路线版本快照 */
    private String routingVersion;

    /** 工艺路线状态：NONE/SNAPSHOT/FAILED */
    private String routingStatus;

    /** MES 下发状态：NOT_SYNCED/PUSHED/FAILED */
    private String mesSyncStatus;

    /** MES 工单编码 */
    private String mesWorkOrderCode;

    /** 最近成功下发时间 */
    private LocalDateTime mesSyncedAt;

    /** 最近一次下发/拉取失败摘要 */
    private String mesLastError;
}
