package com.bluewhale.pdm.part.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 零件对应的 ERP 仓库库存明细
 *
 * @author BlueWhale
 */
@Data
public class PdmPartErpWarehouseVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 仓库ID */
    private Long warehouseId;

    /** 仓库编码 */
    private String warehouseCode;

    /** 仓库名称 */
    private String warehouseName;

    /** 该仓库数量 */
    private BigDecimal quantity;
}
