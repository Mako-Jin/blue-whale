package com.bluewhale.erp.mfg.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * MRP 计划订单实体，对应表 {@code erp_mfg_mrp_planned_order}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_mfg_mrp_planned_order")
public class MrpPlannedOrderEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 运行记录 ID */
    private Long runId;

    /** 计划订单号 */
    private String orderNo;

    /** 建议类型 */
    private String orderType;

    /** 物料主键 */
    private Long itemId;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 计量单位 */
    private String uom;

    /** 毛需求量 */
    private BigDecimal grossQuantity;

    /** 现货可用量 */
    private BigDecimal availableQuantity;

    /** 净需求量 */
    private BigDecimal netQuantity;

    /** BOM 最小层级 */
    private Integer itemLevel;

    /** 来源 BOM 编号 */
    private String bomNo;

    /** 来源 BOM 版本 */
    private String bomVersion;

    /** 需求日期 */
    private LocalDate needDate;

    /** 状态 */
    private String status;
}
