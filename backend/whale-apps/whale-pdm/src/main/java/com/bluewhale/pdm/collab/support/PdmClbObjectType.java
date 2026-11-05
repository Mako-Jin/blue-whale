package com.bluewhale.pdm.collab.support;

import java.util.Set;

/**
 * 交付物/评审对象类型
 *
 * @author BlueWhale
 */
public final class PdmClbObjectType {

    /** 图文档版本 */
    public static final String DOCUMENT = "DOCUMENT";

    /** BOM 版本 */
    public static final String BOM = "BOM";

    /** 工艺路线版本 */
    public static final String ROUTE = "ROUTE";

    /** 评审可引用的零件 */
    public static final String PART = "PART";

    private static final Set<String> DELIVERABLE_TYPES = Set.of(DOCUMENT, BOM, ROUTE);

    private static final Set<String> REVIEW_TYPES = Set.of(DOCUMENT, BOM, ROUTE, PART);

    /**
     * 工具类禁止实例化
     */
    private PdmClbObjectType() {
    }

    /**
     * 校验交付物对象类型
     *
     * @param type 类型
     * @return true 表示合法
     */
    public static boolean isValidDeliverable(String type) {
        return DELIVERABLE_TYPES.contains(type);
    }

    /**
     * 校验评审对象类型
     *
     * @param type 类型
     * @return true 表示合法
     */
    public static boolean isValidReview(String type) {
        return REVIEW_TYPES.contains(type);
    }
}
