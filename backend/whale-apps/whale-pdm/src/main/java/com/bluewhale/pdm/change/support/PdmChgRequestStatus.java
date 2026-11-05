package com.bluewhale.pdm.change.support;

/**
 * 变更申请状态常量。
 *
 * <p>状态机：草稿 → 已提交 → 已通过 / 已驳回 → 已关闭。</p>
 *
 * @author BlueWhale
 */
public final class PdmChgRequestStatus {

    /** 草稿 */
    public static final String DRAFT = "DRAFT";

    /** 已提交待审批 */
    public static final String SUBMITTED = "SUBMITTED";

    /** 审批通过 */
    public static final String APPROVED = "APPROVED";

    /** 审批驳回 */
    public static final String REJECTED = "REJECTED";

    /** 已关闭 */
    public static final String CLOSED = "CLOSED";

    /**
     * 工具类禁止实例化
     */
    private PdmChgRequestStatus() {
    }
}
