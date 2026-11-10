package com.bluewhale.map.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务打点视图。
 *
 * @author BlueWhale
 */
@Data
public class MapLocationVO {

    /** 业务类型 */
    private String bizType;

    /** 业务对象ID */
    private String bizId;

    /** 经度（展示坐标系） */
    private double lng;

    /** 纬度（展示坐标系） */
    private double lat;

    /** 坐标系 */
    private String coordType;

    /** 地址 */
    private String address;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 区县 */
    private String district;

    /** 行政区划码 */
    private String adcode;

    /** 坐标来源供应商 */
    private String provider;

    /** 来源：GEOCODE / MANUAL / IMPORT */
    private String source;

    /** 备注 */
    private String remark;

    /** 最近更新时间 */
    private LocalDateTime updateTime;

    /** 距查询点的距离（米，仅附近查询返回） */
    private Double distanceMeters;
}
