package com.bluewhale.pdm.collab.support;

/**
 * 交付物状态与类型常量
 *
 * @author BlueWhale
 */
public final class PdmClbDeliverableStatus {

    /** 已归档 */
    public static final String ARCHIVED = "ARCHIVED";

    /** 交付物类型：设计资料 */
    public static final String TYPE_DESIGN = "DESIGN";

    /** 交付物类型：图纸 */
    public static final String TYPE_DRAWING = "DRAWING";

    /** 交付物类型：报告 */
    public static final String TYPE_REPORT = "REPORT";

    /** 交付物类型：其他 */
    public static final String TYPE_OTHER = "OTHER";

    /**
     * 工具类禁止实例化
     */
    private PdmClbDeliverableStatus() {
    }

    /**
     * 校验交付物类型
     *
     * @param type 类型
     * @return true 表示合法
     */
    public static boolean isValidType(String type) {
        return TYPE_DESIGN.equals(type) || TYPE_DRAWING.equals(type)
                || TYPE_REPORT.equals(type) || TYPE_OTHER.equals(type);
    }
}
