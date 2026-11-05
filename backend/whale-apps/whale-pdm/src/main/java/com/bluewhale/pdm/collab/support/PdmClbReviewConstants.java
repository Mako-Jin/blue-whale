package com.bluewhale.pdm.collab.support;

import java.util.Set;

/**
 * 评审类型、状态与结论常量
 *
 * @author BlueWhale
 */
public final class PdmClbReviewConstants {

    /** 设计评审 */
    public static final String TYPE_DESIGN = "DESIGN";

    /** 样机评审 */
    public static final String TYPE_PROTOTYPE = "PROTOTYPE";

    /** 转产评审 */
    public static final String TYPE_TRANSFER = "TRANSFER";

    /** 草稿 */
    public static final String STATUS_DRAFT = "DRAFT";

    /** 已关闭 */
    public static final String STATUS_CLOSED = "CLOSED";

    /** 通过 */
    public static final String CONCLUSION_PASSED = "PASSED";

    /** 有条件通过 */
    public static final String CONCLUSION_CONDITIONAL = "CONDITIONAL";

    /** 拒绝 */
    public static final String CONCLUSION_REJECTED = "REJECTED";

    private static final Set<String> TYPES = Set.of(TYPE_DESIGN, TYPE_PROTOTYPE, TYPE_TRANSFER);

    private static final Set<String> CONCLUSIONS =
            Set.of(CONCLUSION_PASSED, CONCLUSION_CONDITIONAL, CONCLUSION_REJECTED);

    /**
     * 工具类禁止实例化
     */
    private PdmClbReviewConstants() {
    }

    /**
     * 校验评审类型
     *
     * @param type 类型
     * @return true 表示合法
     */
    public static boolean isValidType(String type) {
        return TYPES.contains(type);
    }

    /**
     * 校验评审结论
     *
     * @param conclusion 结论
     * @return true 表示合法
     */
    public static boolean isValidConclusion(String conclusion) {
        return CONCLUSIONS.contains(conclusion);
    }
}
