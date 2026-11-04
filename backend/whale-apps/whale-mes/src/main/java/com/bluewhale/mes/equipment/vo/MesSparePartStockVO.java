package com.bluewhale.mes.equipment.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 备件库存视图
 *
 * @author BlueWhale
 */
@Data
public class MesSparePartStockVO {

    /** 库存行 ID */
    private Long id;

    /** 备件 ID */
    private Long partId;

    /** 备件编码 */
    private String partCode;

    /** 仓库编码 */
    private String warehouseCode;

    /** 库存数量 */
    private BigDecimal quantity;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
