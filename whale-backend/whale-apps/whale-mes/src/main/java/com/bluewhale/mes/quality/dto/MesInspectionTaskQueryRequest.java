package com.bluewhale.mes.quality.dto;

import lombok.Data;

/**
 * 检验任务分页查询条件
 *
 * @author BlueWhale
 */
@Data
public class MesInspectionTaskQueryRequest {

    /** 检验任务号（模糊） */
    private String taskNo;

    /** 任务类型 */
    private String taskType;

    /** 工单 ID */
    private Long workOrderId;

    /** 工单编码（模糊） */
    private String workOrderCode;

    /** 工序编码 */
    private String operationCode;

    /** 状态 */
    private String status;

    /** 当前页 */
    private Long current;

    /** 每页条数 */
    private Long size;
}
