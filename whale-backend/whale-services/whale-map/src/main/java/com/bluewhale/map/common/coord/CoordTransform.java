package com.bluewhale.map.common.coord;

/**
 * 坐标系转换。
 *
 * <p><b>为什么可以互相转换却仍要区分</b>：GCJ-02 与 BD-09 都是 WGS84 经过确定算法加偏得到的，
 * 算法公开且可逆，所以「转得回来」。但反算 GCJ-02 → WGS84 没有解析解，只能迭代逼近；
 * 每多做一次往返就多一层数值误差。因此本服务的策略是：<b>存储只放 WGS84，展示只出 GCJ-02</b>，
 * 转换只发生在出入口各一次，链路中间绝不来回倒手。</p>
 *
 * <p><b>境外不偏移</b>：加偏算法只在中国境内生效，境外坐标直接原样返回，
 * 否则把东京的坐标「加偏」到海里。</p>
 *
 * @author BlueWhale
 */
public final class CoordTransform {

    /** 克拉索夫斯基椭球长半轴 */
    private static final double ELLIPSOID_A = 6378245.0;

    /** 椭球偏心率平方 */
    private static final double ELLIPSOID_EE = 0.00669342162296594323;

    /** 百度偏移用的大圆周率常数 */
    private static final double X_PI = Math.PI * 3000.0 / 180.0;

    /** 反算迭代次数：5 次可把误差压到 1e-9 度（毫米级） */
    private static final int INVERSE_ITERATIONS = 5;

    /** 百度坐标固定偏移量（经度） */
    private static final double BD_OFFSET_LNG = 0.0065;

    /** 百度坐标固定偏移量（纬度） */
    private static final double BD_OFFSET_LAT = 0.006;

    /** 百度坐标微调系数 */
    private static final double BD_FINE_FACTOR = 0.00002;

    /** 百度坐标角度微调系数 */
    private static final double BD_ANGLE_FACTOR = 0.000003;

    /** 中国境内经度下界 */
    private static final double CHINA_MIN_LNG = 72.004;

    /** 中国境内经度上界 */
    private static final double CHINA_MAX_LNG = 137.8347;

    /** 中国境内纬度下界 */
    private static final double CHINA_MIN_LAT = 0.8293;

    /** 中国境内纬度上界 */
    private static final double CHINA_MAX_LAT = 55.8271;

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private CoordTransform() {
    }

    /**
     * 把任意坐标系的点转换到目标坐标系
     *
     * <p>以 GCJ-02 为中转站：WGS84 与 BD-09 之间没有直接的公开算法，
     * 硬凑一步转换比两次转换的误差更大。</p>
     *
     * @param point  源坐标点
     * @param target 目标坐标系
     * @return 目标坐标系下的坐标点
     */
    public static GeoPoint convert(GeoPoint point, CoordType target) {
        if (point == null) {
            throw new IllegalArgumentException("坐标点不能为空");
        }
        if (target == null) {
            throw new IllegalArgumentException("目标坐标系不能为空");
        }
        if (point.type() == target) {
            return point;
        }
        GeoPoint gcj = toGcj02(point);
        if (target == CoordType.GCJ02) {
            return gcj;
        }
        if (target == CoordType.BD09) {
            return gcj02ToBd09(gcj);
        }
        return gcj02ToWgs84(gcj);
    }

    /**
     * WGS84 → GCJ-02
     *
     * @param wgs84 WGS84 坐标点
     * @return GCJ-02 坐标点
     */
    public static GeoPoint wgs84ToGcj02(GeoPoint wgs84) {
        if (outOfChina(wgs84.lng(), wgs84.lat())) {
            return new GeoPoint(wgs84.lng(), wgs84.lat(), CoordType.GCJ02);
        }
        double dLat = transformLat(wgs84.lng() - 105.0, wgs84.lat() - 35.0);
        double dLng = transformLng(wgs84.lng() - 105.0, wgs84.lat() - 35.0);
        double radLat = wgs84.lat() / 180.0 * Math.PI;
        double magic = Math.sin(radLat);
        magic = 1 - ELLIPSOID_EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((ELLIPSOID_A * (1 - ELLIPSOID_EE)) / (magic * sqrtMagic) * Math.PI);
        dLng = (dLng * 180.0) / (ELLIPSOID_A / sqrtMagic * Math.cos(radLat) * Math.PI);
        return new GeoPoint(wgs84.lng() + dLng, wgs84.lat() + dLat, CoordType.GCJ02);
    }

    /**
     * GCJ-02 → WGS84（迭代反算，无解析解）
     *
     * @param gcj02 GCJ-02 坐标点
     * @return WGS84 坐标点
     */
    public static GeoPoint gcj02ToWgs84(GeoPoint gcj02) {
        double lng = gcj02.lng();
        double lat = gcj02.lat();
        for (int i = 0; i < INVERSE_ITERATIONS; i++) {
            GeoPoint forward = wgs84ToGcj02(new GeoPoint(lng, lat, CoordType.WGS84));
            lng += gcj02.lng() - forward.lng();
            lat += gcj02.lat() - forward.lat();
        }
        return new GeoPoint(lng, lat, CoordType.WGS84);
    }

    /**
     * GCJ-02 → BD-09
     *
     * @param gcj02 GCJ-02 坐标点
     * @return BD-09 坐标点
     */
    public static GeoPoint gcj02ToBd09(GeoPoint gcj02) {
        double x = gcj02.lng();
        double y = gcj02.lat();
        double z = Math.sqrt(x * x + y * y) + BD_FINE_FACTOR * Math.sin(y * X_PI);
        double theta = Math.atan2(y, x) + BD_ANGLE_FACTOR * Math.cos(x * X_PI);
        return new GeoPoint(Math.cos(theta) * z + BD_OFFSET_LNG, Math.sin(theta) * z + BD_OFFSET_LAT, CoordType.BD09);
    }

    /**
     * BD-09 → GCJ-02
     *
     * @param bd09 BD-09 坐标点
     * @return GCJ-02 坐标点
     */
    public static GeoPoint bd09ToGcj02(GeoPoint bd09) {
        double x = bd09.lng() - BD_OFFSET_LNG;
        double y = bd09.lat() - BD_OFFSET_LAT;
        double z = Math.sqrt(x * x + y * y) - BD_FINE_FACTOR * Math.sin(y * X_PI);
        double theta = Math.atan2(y, x) - BD_ANGLE_FACTOR * Math.cos(x * X_PI);
        return new GeoPoint(Math.cos(theta) * z, Math.sin(theta) * z, CoordType.GCJ02);
    }

    /**
     * 判断坐标是否在中国境外
     *
     * @param lng 经度
     * @param lat 纬度
     * @return 境外返回 {@code true}
     */
    public static boolean outOfChina(double lng, double lat) {
        return lng < CHINA_MIN_LNG || lng > CHINA_MAX_LNG || lat < CHINA_MIN_LAT || lat > CHINA_MAX_LAT;
    }

    /**
     * 转换到 GCJ-02（内部分派）
     *
     * @param point 源坐标点
     * @return GCJ-02 坐标点
     */
    private static GeoPoint toGcj02(GeoPoint point) {
        return switch (point.type()) {
            case WGS84 -> wgs84ToGcj02(point);
            case BD09 -> bd09ToGcj02(point);
            case GCJ02 -> point;
        };
    }

    /**
     * 加偏算法的纬度分量
     *
     * @param x 经度偏移量（相对 105）
     * @param y 纬度偏移量（相对 35）
     * @return 纬度加偏量
     */
    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320.0 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    /**
     * 加偏算法的经度分量
     *
     * @param x 经度偏移量（相对 105）
     * @param y 纬度偏移量（相对 35）
     * @return 经度加偏量
     */
    private static double transformLng(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0;
        return ret;
    }
}
