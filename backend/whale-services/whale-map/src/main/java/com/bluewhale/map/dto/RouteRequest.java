package com.bluewhale.map.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 路径规划请求。
 *
 * @author BlueWhale
 */
@Data
public class RouteRequest {

    /** 起点经度 */
    @NotNull(message = "起点经度不能为空")
    private Double originLng;

    /** 起点纬度 */
    @NotNull(message = "起点纬度不能为空")
    private Double originLat;

    /** 终点经度 */
    @NotNull(message = "终点经度不能为空")
    private Double destinationLng;

    /** 终点纬度 */
    @NotNull(message = "终点纬度不能为空")
    private Double destinationLat;

    /** 坐标系（缺省按对外展示坐标系） */
    private String coordType;

    /** 出行方式：DRIVING / WALKING / CYCLING */
    private String mode;
}
