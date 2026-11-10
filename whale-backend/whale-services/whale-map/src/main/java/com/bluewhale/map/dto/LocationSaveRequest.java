package com.bluewhale.map.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 业务打点请求。
 *
 * <p>坐标与地址二选一：给坐标就直接落（如从地图上点选），只给地址就先解析再落。
 * 两者都给时以坐标为准，地址作为展示值——用户手填的地址往往比解析结果更符合他的习惯。</p>
 *
 * @author BlueWhale
 */
@Data
public class LocationSaveRequest {

    /** 地址（与坐标二选一） */
    @Size(max = 200, message = "地址不能超过 200 字")
    private String address;

    /** 经度 */
    private Double lng;

    /** 纬度 */
    private Double lat;

    /** 坐标系（缺省按对外展示坐标系） */
    private String coordType;

    /** 备注 */
    @Size(max = 200, message = "备注不能超过 200 字")
    private String remark;
}
