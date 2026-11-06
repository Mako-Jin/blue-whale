package com.bluewhale.map.common.geo;

import com.bluewhale.map.common.coord.CoordTransform;
import com.bluewhale.map.common.coord.CoordType;
import com.bluewhale.map.common.coord.GeoPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 围栏判定测试：圆、凸/凹多边形、边界、跨 180 度经线、坐标系统一。
 *
 * @author BlueWhale
 */
class GeoFenceTest {

    /** 北京天安门（WGS84） */
    private static final GeoPoint BASE = new GeoPoint(116.404, 39.915, CoordType.WGS84);

    /** 米与距离比较的容差（米） */
    private static final double TOLERANCE = 0.01;

    @Test
    @DisplayName("圆形围栏：中心在内、边界外 30 米、恰在半径上算在内")
    void circleBoundary() {
        FenceHit inside = GeoFence.circle(BASE, 100.0, offset(BASE, 0, 0));
        assertTrue(inside.inside());
        assertEquals(100.0, inside.distanceMeters(), TOLERANCE);

        FenceHit outside = GeoFence.circle(BASE, 100.0, offset(BASE, 130, 0));
        assertFalse(outside.inside());
        assertEquals(-30.0, outside.distanceMeters(), TOLERANCE);

        FenceHit edge = GeoFence.circle(BASE, 100.0, offset(BASE, 100, 0));
        assertTrue(edge.inside(), "恰好落在半径上应算在内，否则打卡会在边界抖动");
    }

    @Test
    @DisplayName("方形围栏：内部点距离取最近的边")
    void squareInner() {
        List<GeoPoint> square = square(BASE, 100.0);
        FenceHit hit = GeoFence.polygon(square, offset(BASE, 30, 0));
        assertTrue(hit.inside());
        assertEquals(70.0, hit.distanceMeters(), TOLERANCE);

        FenceHit outside = GeoFence.polygon(square, offset(BASE, 130, 0));
        assertFalse(outside.inside());
        assertEquals(-30.0, outside.distanceMeters(), TOLERANCE);
    }

    @Test
    @DisplayName("顶点与边上都算在围栏内，距离为 0")
    void vertexAndEdgeIncluded() {
        List<GeoPoint> square = square(BASE, 100.0);
        assertTrue(GeoFence.polygon(square, offset(BASE, -100, -100)).inside(), "顶点应算在内");
        FenceHit onEdge = GeoFence.polygon(square, offset(BASE, 100, 50));
        assertTrue(onEdge.inside(), "边上应算在内");
        assertEquals(0.0, onEdge.distanceMeters(), TOLERANCE);
    }

    @Test
    @DisplayName("凹多边形的凹口不算在内")
    void concaveNotchExcluded() {
        // L 形：右下角 (100,100)–(200,200) 的方块被挖掉
        GeoPoint origin = offset(BASE, -100, -100);
        List<GeoPoint> lShape = new ArrayList<>(List.of(
                origin,
                offset(origin, 200, 0),
                offset(origin, 200, 100),
                offset(origin, 100, 100),
                offset(origin, 100, 200),
                offset(origin, 0, 200)));
        assertTrue(GeoFence.polygon(lShape, offset(origin, 50, 50)).inside(), "L 形长边内应命中");
        assertFalse(GeoFence.polygon(lShape, offset(origin, 150, 150)).inside(), "凹口内不应命中");
    }

    @Test
    @DisplayName("顶点坐标系与查询点不同时，先统一再判定")
    void mixedCoordType() {
        GeoPoint gcjBase = CoordTransform.convert(BASE, CoordType.GCJ02);
        List<GeoPoint> square = square(gcjBase, 100.0);
        // 查询点用 WGS84 表达同一物理位置，左右各 30 米
        FenceHit hit = GeoFence.polygon(square, offset(BASE, 30, 0));
        assertTrue(hit.inside(), "GCJ-02 围栏应能判定 WGS84 查询点");
        assertEquals(70.0, hit.distanceMeters(), 1.0);
    }

    @Test
    @DisplayName("跨 180 度经线的围栏仍然连续")
    void crossingAntimeridian() {
        GeoPoint east = new GeoPoint(179.9999, 0.0, CoordType.WGS84);
        // 东侧 100 米落在 180 之外，用 -179.9991 表达
        List<GeoPoint> square = List.of(
                new GeoPoint(179.9991, -0.0009, CoordType.WGS84),
                new GeoPoint(-179.9991, -0.0009, CoordType.WGS84),
                new GeoPoint(-179.9991, 0.0009, CoordType.WGS84),
                new GeoPoint(179.9991, 0.0009, CoordType.WGS84));
        FenceHit hit = GeoFence.polygon(square, east);
        assertTrue(hit.inside(), "跨经线围栏把 -179.9991 当成 180 之外时必然误判为外");
        assertTrue(hit.distanceMeters() > 0);
    }

    @Test
    @DisplayName("GeoJSON 式闭合点（首尾重复）自动去掉")
    void closingVertexIgnored() {
        List<GeoPoint> open = square(BASE, 100.0);
        List<GeoPoint> closed = new ArrayList<>(open);
        closed.add(open.get(0));
        FenceHit hit = GeoFence.polygon(closed, offset(BASE, 30, 0));
        assertTrue(hit.inside());
        assertEquals(70.0, hit.distanceMeters(), TOLERANCE);
    }

    @Test
    @DisplayName("顶点不足或半径非法直接拒绝")
    void invalidInput() {
        List<GeoPoint> twoPoints = List.of(BASE, offset(BASE, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> GeoFence.polygon(twoPoints, BASE));
        assertThrows(IllegalArgumentException.class, () -> GeoFence.polygon(null, BASE));
        assertThrows(IllegalArgumentException.class, () -> GeoFence.circle(BASE, 0.0, BASE));
    }

    /**
     * 以某点为原点按米偏移构造坐标点
     *
     * @param origin       原点
     * @param eastMeters   向东偏移（米）
     * @param northMeters  向北偏移（米）
     * @return 坐标点
     */
    private static GeoPoint offset(GeoPoint origin, double eastMeters, double northMeters) {
        double dLat = northMeters / (Math.PI * 6371008.8 / 180.0);
        double dLng = eastMeters / LocalProjection.metersPerLngDegree(origin.lat());
        return new GeoPoint(origin.lng() + dLng, origin.lat() + dLat, origin.type());
    }

    /**
     * 以某点为中心构造边长 2×half 的正方形（逆时针）
     *
     * @param center 中心
     * @param half   半边长（米）
     * @return 四个顶点
     */
    private static List<GeoPoint> square(GeoPoint center, double half) {
        return List.of(
                offset(center, -half, -half),
                offset(center, half, -half),
                offset(center, half, half),
                offset(center, -half, half));
    }
}
