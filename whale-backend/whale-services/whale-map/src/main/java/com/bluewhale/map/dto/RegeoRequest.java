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
    @NotNull(message = "经度不能为空")
    @DecimalMin(value = "-180", message = "经度超出范围")
    @DecimalMax(value = "180", message = "经度超出范围")
    private Double lng;

    /** 纬度 */
    @NotNull(message = "纬度不能为空")
    @DecimalMin(value = "-90", message = "纬度超出范围")
    @DecimalMax(value = "90", message = "纬度超出范围")
    private Double lat;

    /** 坐标系（缺省按对外展示坐标系 GCJ-02） */
    private String coordType;

    /** 检索半径（米，可空） */
    private Integer radiusMeters;
}
