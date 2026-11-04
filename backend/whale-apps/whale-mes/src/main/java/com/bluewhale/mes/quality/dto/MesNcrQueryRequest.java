package com.bluewhale.mes.quality.dto;

import lombok.Data;

/**
 * NCR 分页查询条件
 *
 * @author BlueWhale
 */
@Data
public class MesNcrQueryRequest {

    /** NCR 编号（模糊） */
    private String ncrNo;

    /** 工单 ID */
    private Long workOrderId;

    /** 批次号 */
    private String batchNo;

    /** 状态 */
    private String status;

    /** 当前页 */
    private Long current;

    /** 每页条数 */
    private Long size;
}
