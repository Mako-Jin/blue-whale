package com.bluewhale.map.provider;

import com.bluewhale.map.common.coord.GeoPoint;

/**
 * 路径规划查询条件。
 *
 * @param origin      起点（恒为 WGS84）
 * @param destination 终点（恒为 WGS84）
 * @param mode        出行方式
 * @author BlueWhale
 */
public record RouteQuery(GeoPoint origin, GeoPoint destination, RouteMode mode) {

    /**
     * 校验并构造查询条件
     *
     * @param origin      起点
     * @param destination 终点
     * @param mode        出行方式
     */
    public RouteQuery {
        if (origin == null || destination == null) {
            throw new IllegalArgumentException("起终点不能为空");
        }
        if (mode == null) {
            mode = RouteMode.DRIVING;
        }
    }
}
