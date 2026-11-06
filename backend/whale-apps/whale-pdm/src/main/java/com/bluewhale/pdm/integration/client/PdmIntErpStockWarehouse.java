package com.bluewhale.pdm.integration.client;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * ERP 仓库库存明细
 *
 * @param warehouseId   仓库ID
 * @param warehouseCode 仓库编码
 * @param warehouseName 仓库名称
 * @param quantity      该仓库数量
 * @author BlueWhale
 */
public record PdmIntErpStockWarehouse(Long warehouseId, String warehouseCode, String warehouseName,
                                      BigDecimal quantity) implements Serializable {
}
