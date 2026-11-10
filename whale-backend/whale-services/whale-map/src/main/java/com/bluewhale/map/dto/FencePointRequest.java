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
    @NotNull(message = "顶点经度不能为空")
    @DecimalMin(value = "-180", message = "经度不能小于 -180")
    @DecimalMax(value = "180", message = "经度不能大于 180")
    private Double lng;

    /** 纬度 */
    @NotNull(message = "顶点纬度不能为空")
    @DecimalMin(value = "-90", message = "纬度不能小于 -90")
    @DecimalMax(value = "90", message = "纬度不能大于 90")
    private Double lat;
}
