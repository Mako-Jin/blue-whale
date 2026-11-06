package com.bluewhale.oa.approval.domain.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Workflow 流程实例快照。
 *
 * @author BlueWhale
 */
@Data
public class WorkflowInstanceSnapshot {

    /** 流程实例ID */
    private Long id;

    /** 流程定义编码 */
    private String definitionCode;

    /** 实例标题 */
    private String title;

    /** 业务键 */
    private String businessKey;

    /** 状态：0 运行中 / 1 已通过 / 2 已驳回 / 3 已撤销 */
    private Integer status;

    /** 当前节点键 */
    private String currentNodeKey;

    /** 发起人账号 */
    private String initiator;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 任务轨迹（详情时填充） */
    private List<WorkflowTaskSnapshot> tasks;
}
