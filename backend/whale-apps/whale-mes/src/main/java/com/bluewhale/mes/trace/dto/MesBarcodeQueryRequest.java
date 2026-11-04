package com.bluewhale.mes.trace.dto;

import lombok.Data;

/**
 * 条码分页查询条件
 *
 * @author BlueWhale
 */
@Data
public class MesBarcodeQueryRequest {

    /** 条码值（模糊） */
    private String barcodeValue;

    /** 批次号 */
    private String batchNo;

    /** 物料编码 */
    private String itemCode;

    /** 状态：ACTIVE / VOID */
    private String status;

    /** 当前页 */
    private Long current;

    /** 每页条数 */
    private Long size;
}
