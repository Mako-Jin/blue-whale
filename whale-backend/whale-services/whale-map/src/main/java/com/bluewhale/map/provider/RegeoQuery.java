package com.bluewhale.map.provider;

import com.bluewhale.map.common.coord.GeoPoint;

/**
 * 逆地理查询条件。
 *
 * @param point           坐标（恒为 WGS84）
 * @param radiusMeters    检索半径（米），影响返回的最邻近 POI
 * @author BlueWhale
 */
public record RegeoQuery(GeoPoint point, int radiusMeters) {

    /** 默认检索半径（米） */
    private static final int DEFAULT_RADIUS = 1000;

    /** 半径上限（米） */
    private static final int MAX_RADIUS = 3000;

    /**
     * 校验并构造查询条件
     *
     * @param point        坐标
     * @param radiusMeters 检索半径
     */
    public RegeoQuery {
        if (point == null) {
            throw new IllegalArgumentException("坐标不能为空");
        }
        if (radiusMeters <= 0) {
            radiusMeters = DEFAULT_RADIUS;
        }
        radiusMeters = Math.min(radiusMeters, MAX_RADIUS);
    }
}
