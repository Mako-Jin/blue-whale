package com.bluewhale.map.vo;

import lombok.Data;

/**
 * 逆地理结果视图。
 *
 * @author BlueWhale
 */
@Data
public class RegeoVO {

    /** 经度（展示坐标系，回显请求点） */
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

    /** 街道 */
    private String street;

    /** 门牌号 */
    private String streetNumber;

    /** 实际提供结果的供应商 */
    private String provider;
}
