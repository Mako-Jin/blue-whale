package com.bluewhale.mes.trace.dto;

import lombok.Data;

/**
 * 线边库存分页查询条件
 *
 * @author BlueWhale
 */
@Data
public class MesLineSideStockQueryRequest {

    /** 产线 ID */
    private Long lineId;

    /** 线边仓编码 */
    private String warehouseCode;

    /** 物料编码 */
    private String itemCode;

    /** 批次号 */
    private String batchNo;

    /** 当前页 */
    private Long current;

    /** 每页条数 */
    private Long size;
}
