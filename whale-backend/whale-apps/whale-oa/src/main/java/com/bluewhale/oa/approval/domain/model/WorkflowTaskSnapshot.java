package com.bluewhale.oa.approval.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Workflow 任务快照。
 *
 * @author BlueWhale
 */
@Data
public class WorkflowTaskSnapshot {

    /** 任务ID */
    private Long id;

    /** 流程实例ID */
    private Long instanceId;

    /** 节点键 */
    private String nodeKey;

    /** 节点名称 */
    private String nodeName;

    /** 审批人账号 */
    private String assignee;

    /** 任务归属人（转办后仍为原审批人） */
    private String ownerId;

    /** 委派来源人 */
    private String delegateFrom;

    /** 状态：0 待办 / 1 已同意 / 2 已驳回 / 3 已取消 */
    private Integer status;

    /** 审批意见 */
    private String comment;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
