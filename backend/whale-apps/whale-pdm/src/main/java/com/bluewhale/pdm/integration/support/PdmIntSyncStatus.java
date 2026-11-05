package com.bluewhale.pdm.integration.support;

/**
 * 集成同步状态常量。
 *
 * @author BlueWhale
 */
public final class PdmIntSyncStatus {

    /** 同步成功 */
    public static final String SUCCESS = "SUCCESS";

    /** 同步失败（可重试） */
    public static final String FAILED = "FAILED";

    /**
     * 工具类禁止实例化
     */
    private PdmIntSyncStatus() {
    }
}
