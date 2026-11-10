package com.bluewhale.doc.support.collab;

/**
 * 协作光标/头像配色（M2）。
 *
 * @author BlueWhale
 */
public final class DocPresencePalette {

    /** 与前端 CSS 变量一致的协作色板 */
    private static final String[] COLORS = {
            "#2563eb", "#7c3aed", "#db2777", "#ea580c", "#16a34a", "#0891b2", "#ca8a04", "#dc2626"
    };

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private DocPresencePalette() {
    }

    /**
     * 按用户ID选择稳定颜色。
     *
     * @param userId 用户ID
     * @return 十六进制颜色
     */
    public static String color(Long userId) {
        long value = userId == null ? 0L : userId;
        return COLORS[(int) Math.floorMod(value, COLORS.length)];
    }
}
