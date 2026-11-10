package com.bluewhale.pdm.change.support;

/**
 * 变更追溯动作常量。
 *
 * @author BlueWhale
 */
public final class PdmChgTraceAction {

    /** 创建 */
    public static final String CREATE = "CREATE";

    /** 修改 */
    public static final String UPDATE = "UPDATE";

    /** 提交 */
    public static final String SUBMIT = "SUBMIT";

    /** 审批通过 */
    public static final String APPROVE = "APPROVE";

    /** 审批驳回 */
    public static final String REJECT = "REJECT";

    /** 影响评估 */
    public static final String ASSESS = "ASSESS";

    /** 开始执行 */
    public static final String EXECUTE = "EXECUTE";

    /** 任务完成 */
    public static final String TASK_COMPLETE = "TASK_COMPLETE";

    /** 完成 */
    public static final String COMPLETE = "COMPLETE";

    /** 取消 */
    public static final String CANCEL = "CANCEL";

    /** 关闭 */
    public static final String CLOSE = "CLOSE";

    /** 通知 */
    public static final String NOTIFY = "NOTIFY";

    /** 生效 */
    public static final String EFFECTIVE = "EFFECTIVE";

    /** 旧版本作废 */
    public static final String OBSOLETE = "OBSOLETE";

    /**
     * 工具类禁止实例化
     */
    private PdmChgTraceAction() {
    }
}
