package com.bluewhale.erp.mfg.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * MRP 计划订单（按物料聚合的采购/生产建议）。
 *
 * <p>不变量：净需求 = max(0, 毛需求 - 可用量)，且仅净需求大于 0 时存在。</p>
 *
 * @author BlueWhale
 */
@Data
public class MrpPlannedOrder {

    /** 主键 */
    private Long id;

    /** 运行记录 ID */
    private Long runId;

    /** 计划订单号 */
    private String orderNo;

    /** 建议类型：PURCHASE / PRODUCTION */
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

    /** 需求日期（来源行最早日期） */
    private LocalDate needDate;

    /** 状态：PLANNED */
    private String status;
}
