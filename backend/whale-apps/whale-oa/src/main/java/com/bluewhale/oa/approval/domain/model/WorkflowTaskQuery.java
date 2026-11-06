package com.bluewhale.oa.approval.domain.model;

import lombok.Data;

/**
 * Workflow 任务查询条件。
 *
 * @author BlueWhale
 */
@Data
public class WorkflowTaskQuery {

    /** 审批人账号 */
    private String assignee;

    /** 任务状态 */
    private Integer status;

    /** 流程实例ID */
    private Long instanceId;

    /** 操作人用户ID */
    private Long operatorId;

    /** 是否仅已处理 */
    private Boolean handled;

    /** 「我审批的」视角账号 */
    private String actorAccount;

    /** 「我审批的」视角用户ID */
    private Long actorId;

    /** 页码 */
    private long current = 1;

    /** 每页条数 */
    private long size = 10;
}
