package com.bluewhale.map.common.geo;

/**
 * 局部等距投影（equirectangular）。
 *
 * <p>把一个经纬度点投到「以原点为 (0,0) 的米平面」，用于围栏内外判定与到边界距离计算。
 * 经纬度不能直接当平面坐标做几何运算：经度 1 度在北京约 85 公里、在赤道约 111 公里，
 * 直接按度数算，南北向与东西向的尺度差能到三成，围栏会变成「南北方向被拉长」的椭圆。</p>
 *
 * <p><b>原点一律取查询点本身</b>，不取围栏质心：查询点在投影后恒为 (0,0)，
 * 它自己的坐标不再参与误差，判定结果只承受围栏顶点的投影误差。
 * 几公里范围内该误差小于千分之一（稠密打卡场景通常在 100 米级）。</p>
 *
 * @author BlueWhale
 */
public final class LocalProjection {

    /** 地球平均半径（米，IUGG 推荐值，与 {@link GeoDistance} 保持一致） */
    private static final double EARTH_RADIUS_METERS = 6371008.8;

    /** 1 纬度对应的米数（约 111319.49） */
    private static final double METERS_PER_LAT_DEGREE = Math.PI * EARTH_RADIUS_METERS / 180.0;

    /** 半个地球周长对应的经度跨度（度） */
    private static final double HALF_TURN_DEGREES = 180.0;

    /** 一整圈经度（度），用于把跨 180 度经线的差值折回同一侧 */
    private static final double FULL_TURN_DEGREES = 360.0;

    /** 纬度上限（避免极点处 cos 趋零把东西向尺度压成 0） */
    private static final double MAX_ABS_LAT = 89.9;

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private LocalProjection() {
    }

    /**
     * 给定纬度处 1 经度对应的米数
     *
     * @param lat 纬度
     * @return 米数
     */
    public static double metersPerLngDegree(double lat) {
        double clamped = Math.max(-MAX_ABS_LAT, Math.min(MAX_ABS_LAT, lat));
        return METERS_PER_LAT_DEGREE * Math.cos(Math.toRadians(clamped));
    }

    /**
     * 投影经度分量（米）
     *
     * @param lng       目标经度
     * @param originLng 原点经度
     * @param originLat 原点纬度
     * @return 东西向偏移（米，东为正）
     */
    public static double x(double lng, double originLng, double originLat) {
        return wrapDegrees(lng - originLng) * metersPerLngDegree(originLat);
    }

    /**
     * 投影纬度分量（米）
     *
     * @param lat       目标纬度
     * @param originLat 原点纬度
     * @return 南北向偏移（米，北为正）
     */
    public static double y(double lat, double originLat) {
        return (lat - originLat) * METERS_PER_LAT_DEGREE;
    }

    /**
     * 把经度差折回 [-180, 180]，使跨 180 度经线的围栏仍然连续
     *
     * @param deltaLng 原始经度差
     * @return 折返后的经度差
     */
    private static double wrapDegrees(double deltaLng) {
        if (deltaLng > -HALF_TURN_DEGREES && deltaLng < HALF_TURN_DEGREES) {
            return deltaLng;
        }
        return Math.IEEEremainder(deltaLng, FULL_TURN_DEGREES);
    }
}
