package com.bluewhale.pdm.integration.client;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * ERP 库存只读信息
 *
 * @param itemCode      物料编码
 * @param itemName      物料名称
 * @param totalQuantity 各仓库数量合计
 * @param warehouses    仓库明细
 * @author BlueWhale
 */
public record PdmIntErpStockInfo(String itemCode, String itemName, BigDecimal totalQuantity,
                                 List<PdmIntErpStockWarehouse> warehouses) implements Serializable {
}
