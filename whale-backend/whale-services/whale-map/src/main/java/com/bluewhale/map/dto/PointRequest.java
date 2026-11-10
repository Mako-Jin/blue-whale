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
    @NotNull(message = "经度不能为空")
    @DecimalMin(value = "-180", message = "经度超出范围")
    @DecimalMax(value = "180", message = "经度超出范围")
    private Double lng;

    /** 纬度 */
    @NotNull(message = "纬度不能为空")
    @DecimalMin(value = "-90", message = "纬度超出范围")
    @DecimalMax(value = "90", message = "纬度超出范围")
    private Double lat;
}
