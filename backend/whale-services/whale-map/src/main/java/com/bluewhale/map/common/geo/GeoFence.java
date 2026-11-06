package com.bluewhale.map.common.geo;

import com.bluewhale.map.common.coord.CoordTransform;
import com.bluewhale.map.common.coord.GeoPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * 围栏内外判定（圆 / 多边形）。
 *
 * <p><b>为什么不用 MySQL 的 {@code ST_Contains}</b>：MySQL 的空间函数默认在笛卡尔平面上算，
 * 单位是「度」。经度 1 度与纬度 1 度的实际长度不同，直接算会让围栏在东西向被拉伸；
 * 要修正就得选对 SRID 并保证所有数据同源，而 SRID 写错不会报错，只会让判定悄悄偏几百米。
 * 围栏数量本就很少（每租户几十个，先用 bbox 粗筛），放到应用层按米平面算，
 * 既没有 SRID 口径问题，也能顺手给出「离边界还有多远」。</p>
 *
 * <p><b>坐标系前置条件</b>：这里不猜任何坐标系，顶点与查询点先统一到查询点的坐标系，
 * 再交给 {@link LocalProjection} 投影。围栏从 GCJ-02 底图画出来、点位存的是 WGS84，
 * 这个差异必须在进入本类之前由调用方转换掉——否则算出来的是两个不同坐标系的几何关系。</p>
 *
 * @author BlueWhale
 */
public final class GeoFence {

    /** 多边形最少顶点数 */
    private static final int MIN_VERTICES = 3;

    /** 判定点落在边上的容差（米）：1 微米，只用来吃掉浮点误差 */
    private static final double ON_EDGE_METERS = 1.0E-6;

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private GeoFence() {
    }

    /**
     * 圆形围栏判定
     *
     * @param center       圆心（任意坐标系）
     * @param radiusMeters 半径（米）
     * @param query        查询点（任意坐标系）
     * @return 判定结果
     */
    public static FenceHit circle(GeoPoint center, double radiusMeters, GeoPoint query) {
        if (center == null || query == null) {
            throw new IllegalArgumentException("围栏判定需要圆心与查询点");
        }
        if (!(radiusMeters > 0)) {
            throw new IllegalArgumentException("围栏半径必须大于 0");
        }
        // GeoDistance 会把查询点转到圆心坐标系，调用方无需关心两边是否同源
        double distance = GeoDistance.meters(center, query);
        return FenceHit.of(distance <= radiusMeters, Math.abs(radiusMeters - distance));
    }

    /**
     * 多边形围栏判定（边与顶点都算在内）
     *
     * @param vertices 顶点（顺序沿边界，首尾可不闭合）
     * @param query    查询点
     * @return 判定结果
     */
    public static FenceHit polygon(List<GeoPoint> vertices, GeoPoint query) {
        if (query == null) {
            throw new IllegalArgumentException("围栏判定需要查询点");
        }
        List<GeoPoint> aligned = align(vertices, query);
        Projected plane = project(aligned, query);
        // 查询点投影后恒为原点，所以下面一律按 (0,0) 计算
        double edgeDistance = distanceToEdges(plane, 0.0, 0.0);
        if (edgeDistance <= ON_EDGE_METERS) {
            return FenceHit.onEdge();
        }
        return FenceHit.of(rayCovers(plane, 0.0, 0.0), edgeDistance);
    }

    /**
     * 校验顶点并统一到查询点坐标系
     *
     * @param vertices 原始顶点
     * @param query    查询点
     * @return 统一坐标系后的顶点（已去掉重复的闭合点）
     */
    private static List<GeoPoint> align(List<GeoPoint> vertices, GeoPoint query) {
        if (vertices == null || vertices.size() < MIN_VERTICES) {
            throw new IllegalArgumentException("多边形围栏至少需要 " + MIN_VERTICES + " 个顶点");
        }
        List<GeoPoint> aligned = new ArrayList<>(vertices.size());
        for (GeoPoint vertex : vertices) {
            if (vertex == null) {
                throw new IllegalArgumentException("多边形围栏顶点不能为空");
            }
            aligned.add(CoordTransform.convert(vertex, query.type()));
        }
        dropClosingVertex(aligned);
        if (aligned.size() < MIN_VERTICES) {
            throw new IllegalArgumentException("多边形围栏去掉重复闭合点后不足 " + MIN_VERTICES + " 个顶点");
        }
        return aligned;
    }

    /**
     * 若首尾顶点重合（GeoJSON 习惯），去掉末点，避免产生一条零长度边
     *
     * @param vertices 顶点列表（就地修改）
     */
    private static void dropClosingVertex(List<GeoPoint> vertices) {
        int last = vertices.size() - 1;
        GeoPoint first = vertices.get(0);
        GeoPoint tail = vertices.get(last);
        if (Math.abs(first.lng() - tail.lng()) < 1.0E-9 && Math.abs(first.lat() - tail.lat()) < 1.0E-9) {
            vertices.remove(last);
        }
    }

    /**
     * 以查询点为原点把顶点投到米平面
     *
     * @param vertices 统一坐标系后的顶点
     * @param query    查询点（作为原点）
     * @return 投影结果
     */
    private static Projected project(List<GeoPoint> vertices, GeoPoint query) {
        int size = vertices.size();
        double[] xs = new double[size];
        double[] ys = new double[size];
        for (int i = 0; i < size; i++) {
            GeoPoint vertex = vertices.get(i);
            xs[i] = LocalProjection.x(vertex.lng(), query.lng(), query.lat());
            ys[i] = LocalProjection.y(vertex.lat(), query.lat());
        }
        return new Projected(xs, ys);
    }

    /**
     * 点到各边的最短距离（米）
     *
     * @param plane 多边形（米平面）
     * @param px    点 x
     * @param py    点 y
     * @return 最短距离
     */
    private static double distanceToEdges(Projected plane, double px, double py) {
        double best = Double.MAX_VALUE;
        int size = plane.xs().length;
        for (int i = 0; i < size; i++) {
            int next = (i + 1) % size;
            double distance = distanceToSegment(plane.xs()[i], plane.ys()[i],
                    plane.xs()[next], plane.ys()[next], px, py);
            if (distance < best) {
                best = distance;
            }
        }
        return best;
    }

    /**
     * 点到线段的最短距离
     *
     * @param x1 线段起点 x
     * @param y1 线段起点 y
     * @param x2 线段终点 x
     * @param y2 线段终点 y
     * @param px 点 x
     * @param py 点 y
     * @return 距离（米）
     */
    private static double distanceToSegment(double x1, double y1, double x2, double y2, double px, double py) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared == 0.0) {
            return Math.hypot(px - x1, py - y1);
        }
        double ratio = ((px - x1) * dx + (py - y1) * dy) / lengthSquared;
        double clamped = Math.max(0.0, Math.min(1.0, ratio));
        return Math.hypot(px - (x1 + clamped * dx), py - (y1 + clamped * dy));
    }

    /**
     * 射线法（PNPOLY）判断点是否在多边形内部。
     *
     * <p>从判定点向 +x 方向发射线，统计与各边的交叉次数，奇数次在内。
     * 顶点恰好落在射线上是经典误判来源，这里用 {@code (yi > py) != (yj > py)} 半开区间
     * 把「正好穿过顶点」算作一次，避免重复计数。</p>
     *
     * @param plane 多边形（米平面）
     * @param px    点 x
     * @param py    点 y
     * @return 是否在内
     */
    private static boolean rayCovers(Projected plane, double px, double py) {
        boolean inside = false;
        int size = plane.xs().length;
        for (int i = 0, j = size - 1; i < size; j = i++) {
            double xi = plane.xs()[i];
            double yi = plane.ys()[i];
            double xj = plane.xs()[j];
            double yj = plane.ys()[j];
            if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    /**
     * 米平面上的多边形
     *
     * @param xs 各顶点 x
     * @param ys 各顶点 y
     */
    private record Projected(double[] xs, double[] ys) {
    }
}
