package com.bluewhale.pdm.process.support;

import java.util.Set;

/**
 * 工装类型常量与校验。
 *
 * @author BlueWhale
 */
public final class PdmPrcToolingTypes {

    /** 刀具 */
    public static final String TOOL = "TOOL";

    /** 夹具 */
    public static final String FIXTURE = "FIXTURE";

    /** 模具 */
    public static final String MOLD = "MOLD";

    /** 量具 */
    public static final String GAUGE = "GAUGE";

    /** 允许的类型集合 */
    private static final Set<String> ALLOWED = Set.of(TOOL, FIXTURE, MOLD, GAUGE);

    /**
     * 工具类禁止实例化
     */
    private PdmPrcToolingTypes() {
    }

    /**
     * 判断类型是否合法。
     *
     * @param type 类型
     * @return 是否合法
     */
    public static boolean isValid(String type) {
        return type != null && ALLOWED.contains(type);
    }
}
