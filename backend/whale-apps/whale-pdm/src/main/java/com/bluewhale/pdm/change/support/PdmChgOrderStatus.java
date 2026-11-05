package com.bluewhale.pdm.change.support;

/**
 * 变更单（ECO）状态常量。
 *
 * <p>状态机：草稿 → 评估中 → 审批中 → 已通过 → 执行中 → 已完成 → 已关闭；
 * 旁支为已驳回 / 已取消。</p>
 *
 * @author BlueWhale
 */
public final class PdmChgOrderStatus {

    /** 草稿 */
    public static final String DRAFT = "DRAFT";

    /** 评估中 */
    public static final String ASSESSING = "ASSESSING";

    /** 审批中 */
    public static final String APPROVING = "APPROVING";

    /** 审批通过 */
    public static final String APPROVED = "APPROVED";

    /** 执行中 */
    public static final String EXECUTING = "EXECUTING";

    /** 已完成 */
    public static final String COMPLETED = "COMPLETED";

    /** 已关闭 */
    public static final String CLOSED = "CLOSED";

    /** 已驳回 */
    public static final String REJECTED = "REJECTED";

    /** 已取消 */
    public static final String CANCELLED = "CANCELLED";

    /**
     * 工具类禁止实例化
     */
    private PdmChgOrderStatus() {
    }
}
