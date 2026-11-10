package com.bluewhale.map.common.coord;

/**
 * 带坐标系的经纬度点。
 *
 * <p>坐标系是点的必填部分，不允许「默认 GCJ-02」。同一对数字配上不同坐标系是不同位置，
 * 缺省就等于把 300 米的误差藏进后续每一次距离计算里。</p>
 *
 * @param lng  经度（-180 ~ 180）
 * @param lat  纬度（-90 ~ 90）
 * @param type 坐标系
 * @author BlueWhale
 */
public record GeoPoint(double lng, double lat, CoordType type) {

    /**
     * 校验并构造坐标点
     *
     * @param lng  经度
     * @param lat  纬度
     * @param type 坐标系
     */
    public GeoPoint {
        if (!Double.isFinite(lng) || lng < -180.0 || lng > 180.0) {
            throw new IllegalArgumentException("经度非法：" + lng);
        }
        if (!Double.isFinite(lat) || lat < -90.0 || lat > 90.0) {
            throw new IllegalArgumentException("纬度非法：" + lat);
        }
        if (type == null) {
            throw new IllegalArgumentException("坐标系不能为空");
        }
    }

    /**
     * 判断是否为同一个物理位置（坐标系归一后比较，容差约 1 厘米）
     *
     * @param other 另一个点
     * @return 是否同位置
     */
    public boolean samePlace(GeoPoint other) {
        if (other == null) {
            return false;
        }
        GeoPoint left = CoordTransform.convert(this, CoordType.WGS84);
        GeoPoint right = CoordTransform.convert(other, CoordType.WGS84);
        return Math.abs(left.lng() - right.lng()) < 1.0E-7 && Math.abs(left.lat() - right.lat()) < 1.0E-7;
    }

    /**
     * 格式化为日志/调试用文本
     *
     * @return 形如 {@code 116.404000,39.915000(GCJ02)}
     */
    public String describe() {
        return String.format("%.6f,%.6f(%s)", lng, lat, type);
    }
}
