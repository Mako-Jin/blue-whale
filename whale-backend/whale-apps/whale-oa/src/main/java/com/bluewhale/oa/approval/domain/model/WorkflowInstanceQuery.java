package com.bluewhale.oa.approval.domain.model;

import lombok.Data;

/**
 * Workflow 流程实例查询条件。
 *
 * @author BlueWhale
 */
@Data
public class WorkflowInstanceQuery {

    /** 实例标题（模糊） */
    private String title;

    /** 实例状态 */
    private Integer status;

    /** 流程定义编码 */
    private String definitionCode;

    /** 发起人账号 */
    private String initiator;

    /** 页码 */
    private long current = 1;

    /** 每页条数 */
    private long size = 10;
}
