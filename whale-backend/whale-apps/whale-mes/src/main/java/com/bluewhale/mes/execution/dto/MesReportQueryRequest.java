package com.bluewhale.mes.execution.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 报工记录分页查询条件
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MesReportQueryRequest extends PageQuery {

    /** 任务 ID */
    private Long taskId;

    /** 任务编号 */
    private String taskNo;

    /** 工单 ID */
    private Long workOrderId;

    /** 工单编码 */
    private String workOrderCode;

    /** 报工单号 */
    private String reportNo;
}
