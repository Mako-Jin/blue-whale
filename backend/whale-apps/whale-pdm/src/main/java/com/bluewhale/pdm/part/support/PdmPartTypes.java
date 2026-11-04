package com.bluewhale.pdm.part.support;

import java.util.Set;

/**
 * 零件类型与来源类型常量。
 *
 * @author BlueWhale
 */
public final class PdmPartTypes {

    /** 成品 */
    public static final String PRODUCT = "PRODUCT";

    /** 部件/装配 */
    public static final String ASSEMBLY = "ASSEMBLY";

    /** 零件 */
    public static final String PART = "PART";

    /** 原材料 */
    public static final String RAW_MATERIAL = "RAW_MATERIAL";

    /** 标准件 */
    public static final String STANDARD = "STANDARD";

    /** 通用件 */
    public static final String COMMON = "COMMON";

    /** 唯一件 */
    public static final String UNIQUE = "UNIQUE";

    /** 合法零件类型 */
    public static final Set<String> PART_TYPES = Set.of(PRODUCT, ASSEMBLY, PART, RAW_MATERIAL);

    /** 合法来源类型 */
    public static final Set<String> SOURCE_TYPES = Set.of(STANDARD, COMMON, UNIQUE);

    /**
     * 工具类禁止实例化
     */
    private PdmPartTypes() {
    }

    /**
     * 判断是否可作为 BOM 父件（成品或部件）。
     *
     * @param partType 零件类型
     * @return 是否可装配子件
     */
    public static boolean isAssembly(String partType) {
        return PRODUCT.equals(partType) || ASSEMBLY.equals(partType);
    }
}
