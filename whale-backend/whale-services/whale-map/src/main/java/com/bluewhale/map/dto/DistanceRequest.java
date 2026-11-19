package com.bluewhale.map.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量距离请求。
 *
 * @author BlueWhale
 */
@Data
public class DistanceRequest {

    /** 起点经度 */
    private Double originLng;

    /** 起点纬度 */
    private Double originLat;

    /** 坐标系（缺省按对外展示坐标系） */
    private String coordType;

    /** 目标点列表 */
    @Valid
    @NotEmpty(message = "{validation.map.distance.targets-required}")
    @Size(max = 200, message = "{validation.map.distance.targets-max}")
    private List<PointRequest> targets;
}
