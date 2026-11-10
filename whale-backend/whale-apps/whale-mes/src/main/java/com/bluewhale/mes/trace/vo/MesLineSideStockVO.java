package com.bluewhale.mes.trace.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 线边库存视图对象
 *
 * @author BlueWhale
 */
@Data
public class MesLineSideStockVO {

    /** 库存 ID */
    private Long id;

    /** 产线 ID */
    private Long lineId;

    /** 产线编码 */
    private String lineCode;

    /** 线边仓编码 */
    private String warehouseCode;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 批次号 */
    private String batchNo;

    /** 计量单位 */
    private String uom;

    /** 当前库存数量 */
    private BigDecimal quantity;

    /** 安全库存 */
    private BigDecimal safetyQuantity;

    /** 是否低于安全库存 */
    private Boolean belowSafety;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
