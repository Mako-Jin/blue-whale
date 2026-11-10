package com.bluewhale.erp.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * ERP 内网库存只读视图（按物料编码聚合各仓库余额）
 *
 * @author BlueWhale
 */
@Data
public class ErpInternalStockView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称（取余额快照） */
    private String itemName;

    /** 各仓库数量合计 */
    private BigDecimal totalQuantity;

    /** 仓库明细 */
    private List<WarehouseStock> warehouses = new ArrayList<>();

    /**
     * 仓库库存明细
     */
    @Data
    public static class WarehouseStock implements Serializable {

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
}
