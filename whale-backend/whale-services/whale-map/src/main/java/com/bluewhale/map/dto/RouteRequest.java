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
    @NotNull(message = "{validation.map.route.origin-lng-required}")
    private Double originLng;

    /** 起点纬度 */
    @NotNull(message = "{validation.map.route.origin-lat-required}")
    private Double originLat;

    /** 终点经度 */
    @NotNull(message = "{validation.map.route.destination-lng-required}")
    private Double destinationLng;

    /** 终点纬度 */
    @NotNull(message = "{validation.map.route.destination-lat-required}")
    private Double destinationLat;

    /** 坐标系（缺省按对外展示坐标系） */
    private String coordType;

    /** 出行方式：DRIVING / WALKING / CYCLING */
    private String mode;
}
