package com.bluewhale.map.vo;

import lombok.Data;

/**
 * 坐标点视图（对外恒为展示坐标系）。
 *
 * @author BlueWhale
 */
@Data
public class GeoPointVO {

    /** 经度 */
    private double lng;

    /** 纬度 */
    private double lat;

    /** 坐标系（对外固定为配置的展示坐标系） */
    private String coordType;
}
