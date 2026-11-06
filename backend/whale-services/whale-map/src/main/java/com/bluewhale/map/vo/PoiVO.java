package com.bluewhale.map.vo;

import lombok.Data;

/**
 * 地点条目视图。
 *
 * @author BlueWhale
 */
@Data
public class PoiVO {

    /** 供应商侧 POI 标识 */
    private String id;

    /** 名称 */
    private String name;

    /** 地址 */
    private String address;

    /** 经度（展示坐标系） */
    private double lng;

    /** 纬度（展示坐标系） */
    private double lat;

    /** 坐标系 */
    private String coordType;

    /** 行政区划码 */
    private String adcode;

    /** 分类 */
    private String category;

    /** 距离（米，供应商返回时才有） */
    private Double distanceMeters;
}
