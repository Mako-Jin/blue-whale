package com.bluewhale.map.vo;

import lombok.Data;

/**
 * 地址解析结果视图。
 *
 * @author BlueWhale
 */
@Data
public class GeocodeVO {

    /** 经度（展示坐标系） */
    private double lng;

    /** 纬度（展示坐标系） */
    private double lat;

    /** 坐标系 */
    private String coordType;

    /** 标准地址 */
    private String formattedAddress;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 区县 */
    private String district;

    /** 行政区划码 */
    private String adcode;

    /** 匹配级别 */
    private String level;

    /** 实际提供结果的供应商 */
    private String provider;

    /** 是否命中缓存（前端可据此提示「来自本地缓存」，排障时也有用） */
    private boolean cached;
}
