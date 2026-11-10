package com.bluewhale.map.provider;

import com.bluewhale.map.common.coord.GeoPoint;

import java.util.List;

/**
 * 路径规划结果。
 *
 * @param distanceMeters  里程（米）
 * @param durationMinutes 预计耗时（分钟）
 * @param mode            出行方式
 * @param provider        实际提供结果的供应商名
 * @param polyline        路线折线点（WGS84）；供应商未返回或未解码时为空列表
 * @author BlueWhale
 */
public record RouteResult(double distanceMeters, int durationMinutes, RouteMode mode,
                          String provider, List<GeoPoint> polyline) {

    /**
     * 规范化构造：折线点列表不可为 null
     *
     * @param distanceMeters  里程
     * @param durationMinutes 耗时
     * @param mode            出行方式
     * @param provider        供应商名
     * @param polyline        折线点
     */
    public RouteResult {
        polyline = polyline == null ? List.of() : List.copyOf(polyline);
    }
}
