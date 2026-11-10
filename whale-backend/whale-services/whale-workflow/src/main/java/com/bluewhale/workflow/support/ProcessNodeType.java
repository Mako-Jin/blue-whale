package com.bluewhale.workflow.support;

/**
 * 流程模型节点类型（规范化 JSON 的 {@code nodes[].type} 取值）。
 *
 * <p>支持 7 种类型：开始事件、结束事件、用户任务（审批节点）、排他网关、
 * 并行网关（fork/join）、会签节点、抄送节点。其它类型（服务任务、子流程等）一律不识别，
 * 见 {@code ValidationCode#UNKNOWN_NODE_TYPE}。</p>
 *
 * @author BlueWhale
 */
public enum ProcessNodeType {

    /** 开始事件（流程入口，透传） */
    START_EVENT,

    /** 结束事件（流程终点） */
    END_EVENT,

    /** 用户任务（审批节点，需 assignee） */
    USER_TASK,

    /** 排他网关（多分支择一，需 isDefault 兜底） */
    EXCLUSIVE_GATEWAY,

    /** 并行网关（多出流为 fork，单出流为 join 透传） */
    PARALLEL_GATEWAY,

    /** 会签节点（多人审批，按策略判定节点完成） */
    COUNTER_SIGN,

    /** 抄送节点（非阻塞透传，只发通知，不产生待办） */
    CC_TASK;

    /**
     * 是否为审批类节点（用户任务或会签），运行时需建待办。
     *
     * @return 审批类节点返回 {@code true}
     */
    public boolean isApproval() {
        return this == USER_TASK || this == COUNTER_SIGN;
    }

    /**
     * 是否为抄送节点（非阻塞，只写抄送记录后继续推进）。
     *
     * @return 抄送节点返回 {@code true}
     */
    public boolean isCopy() {
        return this == CC_TASK;
    }

    /**
     * 按文本解析节点类型，不识别返回 {@code null}（大小写不敏感，允许下划线/连字符混排）。
     *
     * @param raw 类型文本，可为 {@code null}
     * @return 节点类型枚举，不识别返回 {@code null}
     */
    public static ProcessNodeType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase().replace('-', '_');
        for (ProcessNodeType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
