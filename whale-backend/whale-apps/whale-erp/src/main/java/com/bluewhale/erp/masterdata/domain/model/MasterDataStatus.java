package com.bluewhale.erp.masterdata.domain.model;

/**
 * 主数据通用状态常量。
 *
 * @author BlueWhale
 */
public final class MasterDataStatus {

    /** 启用 */
    public static final String ENABLED = "ENABLED";

    /** 禁用 */
    public static final String DISABLED = "DISABLED";

    /** 会计期间打开（可记账） */
    public static final String PERIOD_OPEN = "OPEN";

    /** 会计期间已关账 */
    public static final String PERIOD_CLOSED = "CLOSED";

    /**
     * 私有构造器：常量类，禁止实例化。
     */
    private MasterDataStatus() {
    }
}
