package com.bluewhale.workflow.support.machine;

/**
 * 状态机事件：驱动流程实例状态迁移的外部动作。
 *
 * @author BlueWhale
 */
public enum StateMachineEvent {

    /** 发起（从入口开始） */
    START,

    /** 同意（推进当前审批节点） */
    APPROVE,

    /** 驳回 */
    REJECT,

    /** 撤销 */
    CANCEL,

    /** 透传（跳过非审批节点） */
    COMPLETE
}
