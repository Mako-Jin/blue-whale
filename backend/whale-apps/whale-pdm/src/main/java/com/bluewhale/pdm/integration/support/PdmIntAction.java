package com.bluewhale.pdm.integration.support;

/**
 * 集成同步动作常量。
 *
 * @author BlueWhale
 */
public final class PdmIntAction {

    /** CAD 检入 */
    public static final String CAD_CHECKIN = "CAD_CHECKIN";

    /** CAD 装配 EBOM 导入 */
    public static final String CAD_EBOM_IMPORT = "CAD_EBOM_IMPORT";

    /** ERP 物料推送 */
    public static final String PART_UPSERT = "PART_UPSERT";

    /** ERP 拉取 BOM 快照 */
    public static final String BOM_PULL = "BOM_PULL";

    /** BOM 重新通知 */
    public static final String BOM_NOTIFY = "BOM_NOTIFY";

    /** MES 路线下发 */
    public static final String ROUTE_PUSH = "ROUTE_PUSH";

    /** OA 审批回调 */
    public static final String OA_CALLBACK = "OA_CALLBACK";

    /** OA 状态补偿 */
    public static final String OA_COMPENSATE = "OA_COMPENSATE";

    /**
     * 工具类禁止实例化
     */
    private PdmIntAction() {
    }
}
