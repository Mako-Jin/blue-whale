package com.bluewhale.erp.mfg.domain.model;

/**
 * ERP 与 MES 集成状态常量。
 *
 * @author BlueWhale
 */
public final class MesStatus {

    /** 未下发（草稿或尚未触发） */
    public static final String NOT_SYNCED = "NOT_SYNCED";

    /** 已成功下发 */
    public static final String PUSHED = "PUSHED";

    /** 下发/拉取失败 */
    public static final String FAILED = "FAILED";

    /** 回调类型：完工回传 */
    public static final String CALLBACK_COMPLETION = "COMPLETION";

    /** 回调类型：进度回传 */
    public static final String CALLBACK_PROGRESS = "PROGRESS";

    /** 回调处理：已应用 */
    public static final String APPLIED = "APPLIED";

    /** 回调处理：已拒绝 */
    public static final String REJECTED = "REJECTED";

    /** 回调处理：重复事件 */
    public static final String DUPLICATE = "DUPLICATE";

    /** 回传系统操作人（审计口径） */
    public static final long SYSTEM_OPERATOR_ID = 0L;

    /**
     * 私有构造器：常量类，禁止实例化。
     */
    private MesStatus() {
    }
}
