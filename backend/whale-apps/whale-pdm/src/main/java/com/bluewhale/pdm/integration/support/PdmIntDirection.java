package com.bluewhale.pdm.integration.support;

/**
 * 集成同步方向常量。
 *
 * @author BlueWhale
 */
public final class PdmIntDirection {

    /** PDM 推送到外部 */
    public static final String OUTBOUND = "OUTBOUND";

    /** 外部推送到 PDM */
    public static final String INBOUND = "INBOUND";

    /**
     * 工具类禁止实例化
     */
    private PdmIntDirection() {
    }
}
