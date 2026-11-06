package com.bluewhale.map.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 点位与围栏关系判定请求。
 *
 * <p>打卡场景传 {@code bizType + bizId}（只判该考勤组的围栏与通用围栏），
 * 配送/销售场景传 {@code bizType}（判该类业务全部围栏）。两者都不传则判全部启用围栏。</p>
 *
 * @author BlueWhale
 */
@Data
public class FenceCheckRequest {

    /** 查询点经度 */
    @NotNull(message = "经度不能为空")
    @DecimalMin(value = "-180", message = "经度不能小于 -180")
    @DecimalMax(value = "180", message = "经度不能大于 180")
    private Double lng;

    /** 查询点纬度 */
    @NotNull(message = "纬度不能为空")
    @DecimalMin(value = "-90", message = "纬度不能小于 -90")
    @DecimalMax(value = "90", message = "纬度不能大于 90")
    private Double lat;

    /** 坐标系（可空，缺省按对外展示坐标系） */
    @Size(max = 8, message = "坐标系不能超过 8 字")
    private String coordType;

    /** 限定业务类型（可空） */
    @Size(max = 32, message = "业务类型不能超过 32 字")
    private String bizType;

    /** 限定业务对象ID（可空） */
    @Size(max = 64, message = "业务对象ID不能超过 64 字")
    private String bizId;
}
