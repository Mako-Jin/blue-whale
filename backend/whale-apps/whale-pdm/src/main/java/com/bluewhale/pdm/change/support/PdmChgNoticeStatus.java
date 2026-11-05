package com.bluewhale.pdm.change.support;

/**
 * 变更通知（ECN）状态常量。
 *
 * <p>状态机：草稿 → 已通知 → 已生效 → 已关闭；旁支已取消。</p>
 *
 * @author BlueWhale
 */
public final class PdmChgNoticeStatus {

    /** 草稿 */
    public static final String DRAFT = "DRAFT";

    /** 已通知 */
    public static final String NOTIFIED = "NOTIFIED";

    /** 已生效 */
    public static final String EFFECTIVE = "EFFECTIVE";

    /** 已关闭 */
    public static final String CLOSED = "CLOSED";

    /** 已取消 */
    public static final String CANCELLED = "CANCELLED";

    /**
     * 工具类禁止实例化
     */
    private PdmChgNoticeStatus() {
    }
}
