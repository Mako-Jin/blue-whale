package com.bluewhale.map.vo;

import lombok.Data;

import java.util.List;

/**
 * 路径规划结果视图。
 *
 * @author BlueWhale
 */
@Data
public class RouteVO {

    /** 里程（米） */
    private double distanceMeters;

    /** 预计耗时（分钟） */
    private int durationMinutes;

    /** 出行方式 */
    private String mode;

    /** 实际提供结果的供应商 */
    private String provider;

    /** 路线折线点（展示坐标系；供应商未返回时为空） */
    private List<GeoPointVO> polyline;
}
