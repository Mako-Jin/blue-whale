package com.bluewhale.workflow.support;

import lombok.Data;

import java.util.List;

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

    /** 会签人账号列表（仅 {@link ProcessNodeType#COUNTER_SIGN} 必填） */
    private List<String> assignees;

    /** 会签策略：{@code ALL} / {@code ANY} / {@code RATIO:n}（仅会签节点有效，缺省 ALL） */
    private String counterSignPolicy;

    /** 绑定的表单定义 ID（字符串承载以避免前端精度丢失，仅 {@link ProcessNodeType#USER_TASK} 有意义；可空） */
    private String formId;

    /** 绑定的表单编码（仅 {@link ProcessNodeType#USER_TASK} 有意义，非空时须匹配 {@code ^[A-Za-z0-9_-]{1,64}$}；可空） */
    private String formCode;

    /** 画布横坐标（自由画布设计器持久化用，可空） */
    private Integer x;

    /** 画布纵坐标（自由画布设计器持久化用，可空） */
    private Integer y;

    /**
     * 判断节点是否为审批节点。
     *
     * @return 用户任务返回 {@code true}
     */
    public boolean isUserTask() {
        return type != null && type.isApproval();
    }
}
