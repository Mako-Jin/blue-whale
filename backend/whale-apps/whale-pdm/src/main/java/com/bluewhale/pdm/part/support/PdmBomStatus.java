package com.bluewhale.pdm.part.support;

/**
 * BOM 状态常量。
 *
 * @author BlueWhale
 */
public final class PdmBomStatus {

    /** 草稿（可编辑） */
    public static final String DRAFT = "DRAFT";

    /** 已发布（不可编辑） */
    public static final String RELEASED = "RELEASED";

    /** 已作废（被新版本替代） */
    public static final String OBSOLETE = "OBSOLETE";

    /**
     * 工具类禁止实例化
     */
    private PdmBomStatus() {
    }
}
