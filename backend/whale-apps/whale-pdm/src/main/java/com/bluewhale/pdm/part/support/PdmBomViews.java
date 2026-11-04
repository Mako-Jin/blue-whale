package com.bluewhale.pdm.part.support;

import java.util.Set;

/**
 * BOM 视图常量与校验。
 *
 * <p>EBOM 为设计基线；PBOM/MBOM 由 EBOM 派生，派生后独立维护与发布。</p>
 *
 * @author BlueWhale
 */
public final class PdmBomViews {

    /** 设计 BOM */
    public static final String EBOM = "EBOM";

    /** 工艺 BOM */
    public static final String PBOM = "PBOM";

    /** 制造 BOM */
    public static final String MBOM = "MBOM";

    /** 全部合法视图 */
    public static final Set<String> ALL = Set.of(EBOM, PBOM, MBOM);

    /** 可派生视图 */
    public static final Set<String> DERIVABLE = Set.of(PBOM, MBOM);

    /**
     * 工具类禁止实例化
     */
    private PdmBomViews() {
    }

    /**
     * 判断视图是否合法。
     *
     * @param view 视图
     * @return 是否合法
     */
    public static boolean isValid(String view) {
        return view != null && ALL.contains(view);
    }

    /**
     * 判断视图是否可由 EBOM 派生。
     *
     * @param view 视图
     * @return 是否可派生
     */
    public static boolean isDerivable(String view) {
        return view != null && DERIVABLE.contains(view);
    }
}
