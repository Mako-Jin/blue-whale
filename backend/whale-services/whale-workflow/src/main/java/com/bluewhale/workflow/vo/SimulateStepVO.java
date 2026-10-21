package com.bluewhale.workflow.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 流程选路模拟的单个节点步骤。
 *
 * <p>{@code flowKey}/{@code condition}/{@code isDefault} 描述「进入本节点所走的流转」，
 * 首节点（开始事件）为空；据此可还原每个网关实际选中的分支。</p>
 *
 * @author BlueWhale
 */
@Data
public class SimulateStepVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 步骤序号（1 起） */
    private int order;

    /** 节点键 */
    private String nodeKey;

    /** 节点名称（可空） */
    private String nodeName;

    /** 节点类型（ProcessNodeType 名称） */
    private String nodeType;

    /** 进入本节点的流转 key（首节点为空） */
    private String flowKey;

    /** 进入本节点的流转条件（可空） */
    private String condition;

    /** 进入本节点的流转是否为默认流 */
    private Boolean isDefault;

    /** 审批人（仅 USER_TASK） */
    private String assignee;

    /** 会签人列表（仅 COUNTER_SIGN） */
    private List<String> assignees;

    /** 会签策略（仅 COUNTER_SIGN） */
    private String counterSignPolicy;
}
