package com.bluewhale.workflow.support;

/**
 * 工作流服务统一常量：状态域、会签策略与分页边界。
 *
 * <p>实例 / 任务 / 定义 / 版本快照 / 抄送 / 代理的状态码只在此处定义，
 * 业务代码禁止再声明同义局部常量，避免同一状态在不同层出现漂移。</p>
 *
 * @author BlueWhale
 */
public final class WorkflowConstants {

    /** 实例状态：运行中 */
    public static final int INSTANCE_RUNNING = 0;

    /** 实例状态：已通过 */
    public static final int INSTANCE_APPROVED = 1;

    /** 实例状态：已驳回 */
    public static final int INSTANCE_REJECTED = 2;

    /** 实例状态：已撤销 */
    public static final int INSTANCE_CANCELED = 3;

    /** 任务状态：待办 */
    public static final int TASK_PENDING = 0;

    /** 任务状态：已同意 */
    public static final int TASK_APPROVED = 1;

    /** 任务状态：已驳回 */
    public static final int TASK_REJECTED = 2;

    /** 任务状态：已取消 */
    public static final int TASK_CANCELED = 3;

    /** 版本快照发布状态：草稿 */
    public static final int PUBLISH_DRAFT = 0;

    /** 版本快照发布状态：已发布 */
    public static final int PUBLISH_PUBLISHED = 1;

    /** 流程定义状态：正常 */
    public static final int DEFINITION_ENABLED = 1;

    /** 流程定义状态：禁用 */
    public static final int DEFINITION_DISABLED = 0;

    /** 抄送已读标记：未读 */
    public static final int CC_UNREAD = 0;

    /** 抄送已读标记：已读 */
    public static final int CC_READ = 1;

    /** 代理规则状态：启用 */
    public static final int DELEGATE_ENABLED = 1;

    /** 代理规则状态：停用 */
    public static final int DELEGATE_DISABLED = 0;

    /** 单页最大条数：防止深度分页拖垮数据库 */
    public static final long MAX_PAGE_SIZE = 200L;

    /** 默认每页条数 */
    public static final long DEFAULT_PAGE_SIZE = 10L;

    /** 页码上限：避免超大 offset 击穿数据库 */
    public static final long MAX_PAGE_CURRENT = 10000L;

    /** 会签策略：全员同意 */
    public static final String POLICY_ALL = "ALL";

    /** 会签策略：任一同意 */
    public static final String POLICY_ANY = "ANY";

    /** 会签策略：比例前缀 */
    public static final String POLICY_RATIO_PREFIX = "RATIO:";

    /** 百分比基数 */
    public static final int PERCENT_BASE = 100;

    /** 加签任务类型标记（普通审批节点追加的审批人） */
    public static final String SIGN_TYPE_ADD_SIGN = "ADD_SIGN";

    /** 单次加签人数上限 */
    public static final int MAX_ADD_SIGNERS = 10;

    /** 审批人账号最大长度 */
    public static final int MAX_ASSIGNEE_LENGTH = 64;

    /** 常量类不允许实例化 */
    private WorkflowConstants() {
    }
}
