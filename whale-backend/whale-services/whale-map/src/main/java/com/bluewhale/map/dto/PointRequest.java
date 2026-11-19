package com.bluewhale.map.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 单个坐标点。
 *
 * @author BlueWhale
 */
@Data
public class PointRequest {

    /** 经度 */
    @NotNull(message = "{validation.map.point.lng-required}")
    @DecimalMin(value = "-180", message = "{validation.map.point.lng-range}")
    @DecimalMax(value = "180", message = "{validation.map.point.lng-range}")
    private Double lng;

    /** 纬度 */
    @NotNull(message = "{validation.map.point.lat-required}")
    @DecimalMin(value = "-90", message = "{validation.map.point.lat-range}")
    @DecimalMax(value = "90", message = "{validation.map.point.lat-range}")
    private Double lat;
}
