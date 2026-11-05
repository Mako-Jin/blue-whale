package com.bluewhale.pdm.change.support;

/**
 * 审批来源常量。
 *
 * @author BlueWhale
 */
public final class PdmChgApprovalSource {

    /** OA / Workflow 发起 */
    public static final String OA = "OA";

    /** 人工审批（OA 不可用或未配置） */
    public static final String MANUAL = "MANUAL";

    /**
     * 工具类禁止实例化
     */
    private PdmChgApprovalSource() {
    }
}
