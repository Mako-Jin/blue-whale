package com.bluewhale.map.common.coord;

/**
 * 坐标系类型。
 *
 * <p>三种坐标系的差别不是「精度」，而是「同一栋楼落在不同的经纬度上」：
 * WGS84 是 GPS 原始值，GCJ-02 是国测局加偏（腾讯、高德、天地图在用），
 * BD-09 是百度在 GCJ-02 上再叠加一次偏移。把 GCJ-02 当成 WGS84 存，
 * 落库位置会偏 300–500 米，距离与围栏判定随之全错。</p>
 *
 * @author BlueWhale
 */
public enum CoordType {

    /** 国际标准（GPS、天地图、OpenStreetMap），也是本服务的存储基准 */
    WGS84,

    /** 国测局加密坐标（腾讯、高德），前端展示基准 */
    GCJ02,

    /** 百度坐标，仅在与百度接口交互的适配器边界出现 */
    BD09;

    /**
     * 宽松解析坐标系名，兼容 {@code gcj02} / {@code GCJ-02} 等写法
     *
     * @param text 坐标系文本
     * @return 坐标系类型；无法识别返回 {@code null}
     */
    public static CoordType parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.trim().toUpperCase().replace("-", "").replace("_", "");
        for (CoordType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
