package com.bluewhale.workflow.service.impl.task;

/**
 * 待办任务命令层共享常量。
 *
 * @author BlueWhale
 */
final class TaskConstants {

    /** 实例状态：运行中 */
    static final int INSTANCE_RUNNING = 0;

    /** 实例状态：已通过 */
    static final int INSTANCE_APPROVED = 1;

    /** 实例状态：已驳回 */
    static final int INSTANCE_REJECTED = 2;

    /** 任务状态：待办 */
    static final int TASK_PENDING = 0;

    /** 任务状态：已同意 */
    static final int TASK_APPROVED = 1;

    /** 任务状态：已驳回 */
    static final int TASK_REJECTED = 2;

    /** 任务状态：已取消 */
    static final int TASK_CANCELED = 3;

    /** 单页最大条数：防止深度分页拖垮数据库 */
    static final long MAX_PAGE_SIZE = 200L;

    /** 会签策略：全员同意 */
    static final String POLICY_ALL = "ALL";

    /** 会签策略：任一同意 */
    static final String POLICY_ANY = "ANY";

    /** 会签策略：比例前缀 */
    static final String POLICY_RATIO_PREFIX = "RATIO:";

    /** 百分比基数 */
    static final int PERCENT_BASE = 100;

    /** 加签任务类型标记（普通审批节点追加的审批人） */
    static final String SIGN_TYPE_ADD_SIGN = "ADD_SIGN";

    /** 单次加签人数上限 */
    static final int MAX_ADD_SIGNERS = 10;

    /** 审批人账号最大长度 */
    static final int MAX_ASSIGNEE_LENGTH = 64;

    /** 常量类不允许实例化 */
    private TaskConstants() {
    }
}
