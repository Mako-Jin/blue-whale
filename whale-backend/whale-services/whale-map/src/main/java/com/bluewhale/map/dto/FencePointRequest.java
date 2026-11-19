package com.bluewhale.map.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 围栏顶点。
 *
 * @author BlueWhale
 */
@Data
public class FencePointRequest {

    /** 经度 */
    @NotNull(message = "{validation.map.fence-point.lng-required}")
    @DecimalMin(value = "-180", message = "{validation.map.fence-point.lng-min}")
    @DecimalMax(value = "180", message = "{validation.map.fence-point.lng-max}")
    private Double lng;

    /** 纬度 */
    @NotNull(message = "{validation.map.fence-point.lat-required}")
    @DecimalMin(value = "-90", message = "{validation.map.fence-point.lat-min}")
    @DecimalMax(value = "90", message = "{validation.map.fence-point.lat-max}")
    private Double lat;
}
