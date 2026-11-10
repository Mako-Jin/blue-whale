package com.bluewhale.pdm.part.support;

/**
 * BOM 异步对比任务状态常量。
 *
 * @author BlueWhale
 */
public final class PdmBomDiffTaskStatus {

    /** 已创建，等待执行 */
    public static final String PENDING = "PENDING";

    /** 执行中 */
    public static final String RUNNING = "RUNNING";

    /** 执行成功，结果已写入 */
    public static final String SUCCESS = "SUCCESS";

    /** 执行失败，原因见 error_message */
    public static final String FAILED = "FAILED";

    /**
     * 工具类禁止实例化
     */
    private PdmBomDiffTaskStatus() {
    }
}
