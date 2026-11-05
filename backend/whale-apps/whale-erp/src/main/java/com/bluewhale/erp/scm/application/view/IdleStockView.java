package com.bluewhale.erp.scm.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 呆滞库存视图。
 *
 * @author BlueWhale
 */
@Data
public class IdleStockView {

    /** 仓库主键 */
    private Long warehouseId;

    /** 物料主键 */
    private Long itemId;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 结存数量 */
    private BigDecimal quantity;

    /** 结存金额 */
    private BigDecimal amount;

    /** 最近出库时间（空表示从未出库） */
    private LocalDateTime lastOutboundAt;

    /** 库龄天数（空表示从未出库） */
    private Integer idleDays;
}
