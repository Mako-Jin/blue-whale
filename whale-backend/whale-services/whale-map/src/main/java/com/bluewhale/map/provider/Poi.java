package com.bluewhale.map.provider;

import com.bluewhale.map.common.coord.GeoPoint;

/**
 * 地点条目。
 *
 * @param id             供应商侧 POI 标识（不可跨供应商互认）
 * @param name           名称
 * @param address        地址
 * @param point          坐标（恒为 WGS84）
 * @param adcode         行政区划码
 * @param category       分类（供应商原始分类串）
 * @param distanceMeters 距查询点的距离（米）；未指定查询点时为空
 * @author BlueWhale
 */
public record Poi(String id, String name, String address, GeoPoint point, String adcode,
                  String category, Double distanceMeters) {
}
