package com.bluewhale.pdm.process.support;

/**
 * 工艺路线状态常量。
 *
 * @author BlueWhale
 */
public final class PdmPrcStatus {

    /** 草稿（可编辑） */
    public static final String DRAFT = "DRAFT";

    /** 已发布（不可编辑） */
    public static final String RELEASED = "RELEASED";

    /** 已作废（被新版本替代或撤回） */
    public static final String OBSOLETE = "OBSOLETE";

    /**
     * 工具类禁止实例化
     */
    private PdmPrcStatus() {
    }
}
