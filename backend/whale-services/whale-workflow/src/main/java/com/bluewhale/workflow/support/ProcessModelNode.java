package com.bluewhale.workflow.support;

import lombok.Data;

/**
 * 流程模型节点（规范化 JSON 的 {@code nodes[]} 元素）。
 *
 * @author BlueWhale
 */
@Data
public class ProcessModelNode {

    /** 节点键（流程内唯一，运行时状态标识） */
    private String key;

    /** 节点类型 */
    private ProcessNodeType type;

    /** 节点名称（可空） */
    private String name;

    /** 审批人账号（仅 {@link ProcessNodeType#USER_TASK} 必填） */
    private String assignee;

    /**
     * 判断节点是否为审批节点。
     *
     * @return 用户任务返回 {@code true}
     */
    public boolean isUserTask() {
        return type == ProcessNodeType.USER_TASK;
    }
}
