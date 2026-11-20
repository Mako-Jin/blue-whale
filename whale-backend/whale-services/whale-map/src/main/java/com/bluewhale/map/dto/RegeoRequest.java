package com.bluewhale.map.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 逆地理请求。
 *
 * @author BlueWhale
 */
@Data
public class RegeoRequest {

    /** 经度 */
    @NotNull(message = "{validation.map.regeo.lng-required}")
    @DecimalMin(value = "-180", message = "{validation.map.regeo.lng-range}")
    @DecimalMax(value = "180", message = "{validation.map.regeo.lng-range}")
    private Double lng;

    /** 纬度 */
    @NotNull(message = "{validation.map.regeo.lat-required}")
    @DecimalMin(value = "-90", message = "{validation.map.regeo.lat-range}")
    @DecimalMax(value = "90", message = "{validation.map.regeo.lat-range}")
    private Double lat;

    /** 坐标系（缺省按对外展示坐标系 GCJ-02） */
    private String coordType;

    /** 检索半径（米，可空） */
    private Integer radiusMeters;
}
