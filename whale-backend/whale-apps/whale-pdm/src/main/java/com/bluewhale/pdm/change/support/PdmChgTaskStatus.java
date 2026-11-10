package com.bluewhale.pdm.change.support;

/**
 * 变更任务状态常量。
 *
 * <p>任务只有待完成与已完成两种状态；完成后登记结果版本号。</p>
 *
 * @author BlueWhale
 */
public final class PdmChgTaskStatus {

    /** 待完成 */
    public static final String PENDING = "PENDING";

    /** 已完成 */
    public static final String COMPLETED = "COMPLETED";

    /**
     * 工具类禁止实例化
     */
    private PdmChgTaskStatus() {
    }
}
