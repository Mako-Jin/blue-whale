package com.bluewhale.workflow.support;

/**
 * 驳回目标：驳回即退回，处理人由目标决定。
 *
 * @author BlueWhale
 */
public enum RejectTarget {

    /** 退回发起人（在入口节点生成「重新提交」待办） */
    INITIATOR,

    /** 退回上一审批节点（上一层不唯一时不可用） */
    PREVIOUS,

    /** 退回指定上游审批节点 */
    SPECIFIED
}
