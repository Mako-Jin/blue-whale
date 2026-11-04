package com.bluewhale.mes.quality.dto;

import lombok.Data;

/**
 * 返工单分页查询条件
 *
 * @author BlueWhale
 */
@Data
public class MesReworkQueryRequest {

    /** 返工单号（模糊） */
    private String reworkNo;

    /** NCR 编号 */
    private String ncrNo;

    /** 工单 ID */
    private Long workOrderId;

    /** 状态 */
    private String status;

    /** 当前页 */
    private Long current;

    /** 每页条数 */
    private Long size;
}
