package com.bluewhale.map.provider;

/**
 * 地图能力枚举。
 *
 * <p>路由按能力而不是按供应商配置，是因为实际使用中「逆地理用高德、路径用腾讯」很常见：
 * 各家的强项不同，绑死一家就意味着在某项能力上长期用次优解。能力路由把选择留在配置里，
 * 换供应商不需要改代码。</p>
 *
 * @author BlueWhale
 */
public enum MapCapability {

    /** 地址解析：结构化地址 → 坐标 */
    GEOCODE,

    /** 逆地理：坐标 → 结构化地址 */
    REGEO,

    /** 地点检索 */
    POI,

    /** 路径规划 */
    ROUTE,

    /** 距离计算（本地球面计算，不消耗配额） */
    DISTANCE;

    /**
     * 宽松解析能力名
     *
     * @param text 能力名
     * @return 能力；无法识别返回 {@code null}
     */
    public static MapCapability parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
