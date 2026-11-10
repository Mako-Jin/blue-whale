package com.bluewhale.pdm.process.support;

/**
 * MES 下发状态常量。
 *
 * @author BlueWhale
 */
public final class PdmPrcPublishStatus {

    /** 下发成功 */
    public static final String SUCCESS = "SUCCESS";

    /** 下发失败（可重试） */
    public static final String FAILED = "FAILED";

    /** 已撤回 */
    public static final String WITHDRAWN = "WITHDRAWN";

    /**
     * 工具类禁止实例化
     */
    private PdmPrcPublishStatus() {
    }
}
