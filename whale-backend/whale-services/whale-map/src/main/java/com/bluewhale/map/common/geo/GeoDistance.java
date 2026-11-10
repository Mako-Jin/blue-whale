package com.bluewhale.map.common.geo;

import com.bluewhale.map.common.coord.CoordTransform;
import com.bluewhale.map.common.coord.GeoPoint;

/**
 * 球面距离计算。
 *
 * <p>用 haversine 而非平面勾股：在经度方向 1 度约 85 公里（北京纬度）而赤道是 111 公里，
 * 直接按度数算距离，南北向与东西向的误差能差一倍。</p>
 *
 * <p><b>坐标系必须先统一</b>：两个来自不同供应商的点如果在各自坐标系里直接算，
 * 300 米的加偏会原封不动变成「距离误差」。{@link #meters(GeoPoint, GeoPoint)} 会先把第二个点
 * 转到第一个点的坐标系，调用方无需自己判断。</p>
 *
 * @author BlueWhale
 */
public final class GeoDistance {

    /** 地球平均半径（米，IUGG 推荐值） */
    private static final double EARTH_RADIUS_METERS = 6371008.8;

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private GeoDistance() {
    }

    /**
     * 计算两点间球面距离（米）
     *
     * @param from 起点（以其坐标系为基准）
     * @param to   终点（自动转到起点坐标系）
     * @return 距离（米）
     */
    public static double meters(GeoPoint from, GeoPoint to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("距离计算需要两个坐标点");
        }
        GeoPoint target = CoordTransform.convert(to, from.type());
        double radLat1 = Math.toRadians(from.lat());
        double radLat2 = Math.toRadians(target.lat());
        double deltaLat = radLat2 - radLat1;
        double deltaLng = Math.toRadians(target.lng() - from.lng());
        double sinLat = Math.sin(deltaLat / 2);
        double sinLng = Math.sin(deltaLng / 2);
        double h = sinLat * sinLat + Math.cos(radLat1) * Math.cos(radLat2) * sinLng * sinLng;
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    /**
     * 按给定速度把距离换算成分钟数
     *
     * @param meters      距离（米）
     * @param metersPerSec 速度（米/秒）
     * @return 预计耗时（分钟，向上取整）
     */
    public static int minutes(double meters, double metersPerSec) {
        if (metersPerSec <= 0) {
            throw new IllegalArgumentException("速度必须大于 0");
        }
        return (int) Math.ceil(meters / metersPerSec / 60.0);
    }
}
