package com.bluewhale.erp.mfg.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * MRP 计划订单视图。
 *
 * @author BlueWhale
 */
@Data
public class MrpPlannedOrderView {

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

    /** 需求日期 */
    private LocalDate needDate;

    /** 状态 */
    private String status;

    /** 需求追溯（仅详情返回） */
    private List<MrpTraceView> traces;
}
