package com.bluewhale.map.common.geo;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Geohash 编码与邻域前缀枚举。
 *
 * <p><b>为什么用 geohash 而不是 {@code ST_DWithin}</b>：本服务的点位存储是普通 InnoDB 表，
 * 加空间索引要额外的列类型与 SRID 约束，而 geohash 只用一个 {@code VARCHAR(12)} 列加普通索引
 * 就能把「附近查询」从全表扫缩小到九宫格。代价是要自己做一次球面精筛——
 * 前缀命中只保证「在格子里」，不保证「在半径内」，所以 SQL 之后必须再算一次距离。</p>
 *
 * <p><b>为什么是九宫格而不是单个前缀</b>：点位落在格子边缘时，半径内的邻居在隔壁格子里，
 * 只查当前前缀会稳定漏掉边界上的点，而且漏得很有规律（离得越近越容易漏），
 * 排查时极难发现。</p>
 *
 * @author BlueWhale
 */
public final class GeoHash {

    /** geohash 字母表（去掉 a/i/l/o，避免与数字混淆） */
    private static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";

    /** 单字符承载的比特数 */
    private static final int BITS_PER_CHAR = 5;

    /** 最大编码长度（12 位约 3.7 厘米，已远超业务需要） */
    private static final int MAX_PRECISION = 12;

    /** 赤道处 1 度经度的地面长度（米） */
    private static final double METERS_PER_LNG_DEGREE = 111320.0;

    /** 1 度纬度的地面长度（米） */
    private static final double METERS_PER_LAT_DEGREE = 110540.0;

    /** 纬度合法上界 */
    private static final double MAX_LATITUDE = 90.0;

    /** 经度合法上界 */
    private static final double MAX_LONGITUDE = 180.0;

    /** 完整纬度跨度 */
    private static final double LAT_SPAN = 180.0;

    /** 完整经度跨度 */
    private static final double LNG_SPAN = 360.0;

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private GeoHash() {
    }

    /**
     * 把经纬度编码为指定长度的 geohash
     *
     * @param lng       经度
     * @param lat       纬度
     * @param precision 编码长度（1–12）
     * @return geohash 字符串
     */
    public static String encode(double lng, double lat, int precision) {
        int length = Math.min(Math.max(precision, 1), MAX_PRECISION);
        double minLat = -MAX_LATITUDE;
        double maxLat = MAX_LATITUDE;
        double minLng = -MAX_LONGITUDE;
        double maxLng = MAX_LONGITUDE;
        StringBuilder result = new StringBuilder(length);
        boolean evenBit = true;
        int bit = 0;
        int current = 0;
        while (result.length() < length) {
            if (evenBit) {
                double mid = (minLng + maxLng) / 2;
                boolean upper = lng >= mid;
                current = current * 2 + (upper ? 1 : 0);
                minLng = upper ? mid : minLng;
                maxLng = upper ? maxLng : mid;
            } else {
                double mid = (minLat + maxLat) / 2;
                boolean upper = lat >= mid;
                current = current * 2 + (upper ? 1 : 0);
                minLat = upper ? mid : minLat;
                maxLat = upper ? maxLat : mid;
            }
            evenBit = !evenBit;
            bit++;
            if (bit == BITS_PER_CHAR) {
                result.append(BASE32.charAt(current));
                bit = 0;
                current = 0;
            }
        }
        return result.toString();
    }

    /**
     * 解码 geohash 的边界框
     *
     * @param hash geohash 字符串
     * @return {@code [minLng, minLat, maxLng, maxLat]}
     */
    public static double[] bounds(String hash) {
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("geohash 不能为空");
        }
        double minLat = -MAX_LATITUDE;
        double maxLat = MAX_LATITUDE;
        double minLng = -MAX_LONGITUDE;
        double maxLng = MAX_LONGITUDE;
        boolean evenBit = true;
        for (char ch : hash.trim().toLowerCase().toCharArray()) {
            int value = BASE32.indexOf(ch);
            if (value < 0) {
                throw new IllegalArgumentException("非法 geohash 字符：" + ch);
            }
            for (int mask = 16; mask > 0; mask >>= 1) {
                boolean upper = (value & mask) != 0;
                if (evenBit) {
                    double mid = (minLng + maxLng) / 2;
                    minLng = upper ? mid : minLng;
                    maxLng = upper ? maxLng : mid;
                } else {
                    double mid = (minLat + maxLat) / 2;
                    minLat = upper ? mid : minLat;
                    maxLat = upper ? maxLat : mid;
                }
                evenBit = !evenBit;
            }
        }
        return new double[] {minLng, minLat, maxLng, maxLat};
    }

    /**
     * 按查询半径选择前缀长度
     *
     * <p>取「格子边长不小于半径」的<b>最细</b>前缀：从最长编码往回收，第一个满足条件的长度
     * 就是最贴近半径的那一档。为什么必须「不小于半径」——九宫格只有在格子不小于半径时
     * 才保证覆盖整个查询圆；为什么必须「最细」——格子越粗，候选集越大，精筛白算的部分越多。</p>
     *
     * @param lat          中心纬度
     * @param radiusMeters 查询半径（米）
     * @return 前缀长度
     */
    public static int precisionForRadius(double lat, double radiusMeters) {
        if (radiusMeters <= 0) {
            throw new IllegalArgumentException("半径必须大于 0");
        }
        double cosLat = Math.max(Math.cos(Math.toRadians(Math.min(Math.abs(lat), 89.0))), 1.0E-6);
        for (int precision = MAX_PRECISION; precision >= 1; precision--) {
            int totalBits = precision * BITS_PER_CHAR;
            int lngBits = (totalBits + 1) / 2;
            int latBits = totalBits / 2;
            double heightMeters = LAT_SPAN / Math.pow(2, latBits) * METERS_PER_LAT_DEGREE;
            double widthMeters = LNG_SPAN / Math.pow(2, lngBits) * METERS_PER_LNG_DEGREE * cosLat;
            if (heightMeters >= radiusMeters && widthMeters >= radiusMeters) {
                return precision;
            }
        }
        return 1;
    }

    /**
     * 枚举覆盖查询圆的九宫格前缀
     *
     * @param lng          中心经度
     * @param lat          中心纬度
     * @param radiusMeters 查询半径（米）
     * @return 去重后的 geohash 前缀集合（最多 9 个）
     */
    public static Set<String> coveringPrefixes(double lng, double lat, double radiusMeters) {
        int precision = precisionForRadius(lat, radiusMeters);
        String center = encode(lng, lat, precision);
        double[] box = bounds(center);
        double cellWidth = box[2] - box[0];
        double cellHeight = box[3] - box[1];
        double centerLng = (box[0] + box[2]) / 2;
        double centerLat = (box[1] + box[3]) / 2;
        Set<String> prefixes = new LinkedHashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                double shiftedLng = clampLng(centerLng + dx * cellWidth);
                double shiftedLat = clampLat(centerLat + dy * cellHeight);
                prefixes.add(encode(shiftedLng, shiftedLat, precision));
            }
        }
        return prefixes;
    }

    /**
     * 经度夹取到合法范围
     *
     * @param lng 经度
     * @return 合法经度
     */
    private static double clampLng(double lng) {
        if (lng > MAX_LONGITUDE) {
            return lng - LNG_SPAN;
        }
        if (lng < -MAX_LONGITUDE) {
            return lng + LNG_SPAN;
        }
        return lng;
    }

    /**
     * 纬度夹取到合法范围
     *
     * @param lat 纬度
     * @return 合法纬度
     */
    private static double clampLat(double lat) {
        return Math.min(Math.max(lat, -MAX_LATITUDE), MAX_LATITUDE);
    }
}
