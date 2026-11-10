package com.bluewhale.oa.approval.domain.model;

import lombok.Data;

/**
 * Workflow 流程实例状态摘要。
 *
 * @author BlueWhale
 */
@Data
public class WorkflowInstanceStatusSnapshot {

    /** 流程实例ID */
    private Long id;

    /** 业务键 */
    private String businessKey;

    /** 状态：0 运行中 / 1 已通过 / 2 已驳回 / 3 已撤销 */
    private Integer status;

    /** 当前节点键 */
    private String currentNodeKey;
}
