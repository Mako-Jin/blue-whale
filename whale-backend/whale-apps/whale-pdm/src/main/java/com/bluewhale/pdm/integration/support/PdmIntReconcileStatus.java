package com.bluewhale.pdm.integration.support;

import java.util.Set;

/**
 * 集成对账结论常量。
 *
 * @author BlueWhale
 */
public final class PdmIntReconcileStatus {

    /** 一致 */
    public static final String MATCH = "MATCH";

    /** 存在差异 */
    public static final String DIFF = "DIFF";

    /** 外部缺失 */
    public static final String MISSING = "MISSING";

    /** 合法结论集合 */
    private static final Set<String> VALUES = Set.of(MATCH, DIFF, MISSING);

    /**
     * 工具类禁止实例化
     */
    private PdmIntReconcileStatus() {
    }

    /**
     * 是否为合法对账结论
     *
     * @param value 结论
     * @return true 表示合法
     */
    public static boolean isValid(String value) {
        return VALUES.contains(value);
    }
}
