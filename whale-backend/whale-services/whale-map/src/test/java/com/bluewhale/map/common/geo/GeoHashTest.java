package com.bluewhale.map.common.geo;

import com.bluewhale.map.common.coord.CoordType;
import com.bluewhale.map.common.coord.GeoPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Geohash 与球面距离测试。
 *
 * @author BlueWhale
 */
class GeoHashTest {

    /** 北京天安门（GCJ-02） */
    private static final double LNG = 116.404;

    /** 北京天安门纬度 */
    private static final double LAT = 39.915;

    @Test
    @DisplayName("编码稳定且落在北京区域前缀")
    void encodeStable() {
        String hash = GeoHash.encode(LNG, LAT, 9);
        assertEquals(9, hash.length());
        assertTrue(hash.startsWith("wx4"), "北京 geohash 前缀异常：" + hash);
        assertEquals(hash, GeoHash.encode(LNG, LAT, 9));
    }

    @Test
    @DisplayName("精度越高编码越长，且高精度以低精度为前缀")
    void precisionPrefix() {
        String coarse = GeoHash.encode(LNG, LAT, 5);
        String fine = GeoHash.encode(LNG, LAT, 9);
        assertTrue(fine.startsWith(coarse), "高精度编码应沿用低精度前缀");
    }

    @Test
    @DisplayName("解码边界框包含原点")
    void boundsContainPoint() {
        String hash = GeoHash.encode(LNG, LAT, 7);
        double[] box = GeoHash.bounds(hash);
        assertTrue(box[0] <= LNG && LNG <= box[2], "经度越界");
        assertTrue(box[1] <= LAT && LAT <= box[3], "纬度越界");
    }

    @Test
    @DisplayName("半径越小前缀越长")
    void precisionGrowsAsRadiusShrinks() {
        int wide = GeoHash.precisionForRadius(LAT, 3000);
        int narrow = GeoHash.precisionForRadius(LAT, 100);
        assertTrue(narrow > wide, "半径缩小后精度应提高：" + wide + " → " + narrow);
    }

    @Test
    @DisplayName("九宫格前缀最多 9 个且包含中心前缀")
    void coveringPrefixesBounded() {
        Set<String> prefixes = GeoHash.coveringPrefixes(LNG, LAT, 500);
        assertTrue(prefixes.size() <= 9, "前缀数量超限：" + prefixes.size());
        int precision = GeoHash.precisionForRadius(LAT, 500);
        assertTrue(prefixes.contains(GeoHash.encode(LNG, LAT, precision)), "缺少中心前缀");
    }

    @Test
    @DisplayName("九宫格覆盖半径内的邻居点")
    void coveringPrefixesCatchNeighbor() {
        double radius = 300;
        // 正东方向约 250 米的邻居：落在中心格或右邻格，必须被覆盖
        double neighborLng = LNG + 250 / (111320.0 * Math.cos(Math.toRadians(LAT)));
        int precision = GeoHash.precisionForRadius(LAT, radius);
        String neighborHash = GeoHash.encode(neighborLng, LAT, precision);
        assertTrue(GeoHash.coveringPrefixes(LNG, LAT, radius).contains(neighborHash),
                "邻居格未进入覆盖集合");
    }

    @Test
    @DisplayName("球面距离：北京到上海约 1068 公里")
    void distanceBeijingToShanghai() {
        GeoPoint beijing = new GeoPoint(116.404, 39.915, CoordType.GCJ02);
        GeoPoint shanghai = new GeoPoint(121.473, 31.230, CoordType.GCJ02);
        double km = GeoDistance.meters(beijing, shanghai) / 1000.0;
        assertTrue(km > 1050 && km < 1085, "距离异常：" + km);
    }

    @Test
    @DisplayName("跨坐标系距离自动归一，等价于同坐标系结果")
    void distanceAcrossCoordTypes() {
        GeoPoint wgs = new GeoPoint(116.397428, 39.90923, CoordType.WGS84);
        GeoPoint gcj = com.bluewhale.map.common.coord.CoordTransform.wgs84ToGcj02(wgs);
        double viaMixed = GeoDistance.meters(wgs, gcj);
        double viaSame = GeoDistance.meters(wgs, wgs);
        assertTrue(viaMixed < 1.0, "同一位置跨坐标系算出距离：" + viaMixed);
        assertEquals(0.0, viaSame, 1.0E-6);
    }
}
