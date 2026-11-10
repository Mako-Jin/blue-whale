package com.bluewhale.mes.dispatch.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务分页查询条件
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MesTaskQueryRequest extends PageQuery {

    /** 任务编号 */
    private String taskNo;

    /** 工单 ID */
    private Long workOrderId;

    /** 工单编码 */
    private String workOrderCode;

    /** 工序编码 */
    private String operationCode;

    /** 状态 */
    private String status;

    /** 优先级 */
    private String priority;
}
