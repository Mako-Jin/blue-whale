package com.bluewhale.mes.trace.dto;

import lombok.Data;

/**
 * 线边流水分页查询条件
 *
 * @author BlueWhale
 */
@Data
public class MesLineSideMovementQueryRequest {

    /** 产线 ID */
    private Long lineId;

    /** 物料编码 */
    private String itemCode;

    /** 批次号 */
    private String batchNo;

    /** 流水类型：INBOUND / ISSUE / RETURN / ADJUST */
    private String movementType;

    /** 当前页 */
    private Long current;

    /** 每页条数 */
    private Long size;
}
