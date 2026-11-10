package com.bluewhale.mes.trace.dto;

import lombok.Data;

/**
 * 批次分页查询条件
 *
 * @author BlueWhale
 */
@Data
public class MesBatchQueryRequest {

    /** 批次号（模糊） */
    private String batchNo;

    /** 物料编码 */
    private String itemCode;

    /** 批次类型：RAW / SEMI / FINISHED */
    private String batchType;

    /** 状态：ACTIVE / CLOSED */
    private String status;

    /** 当前页 */
    private Long current;

    /** 每页条数 */
    private Long size;
}
