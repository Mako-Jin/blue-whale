package com.bluewhale.map.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 围栏保存请求（新建或按 {@code id} 覆盖更新）。
 *
 * <p>圆与多边形二选一，由 {@code fenceType} 决定，具体字段校验在服务层做
 * ——用 {@code @NotNull} 表达「二选一」需要分组校验，写在实体上会让另一类的字段
 * 也被强制必填。</p>
 *
 * @author BlueWhale
 */
@Data
public class FenceSaveRequest {

    /** 围栏ID（空则新建） */
    private Long id;

    /** 围栏名称 */
    @NotBlank(message = "{validation.map.fence.name-required}")
    @Size(max = 64, message = "{validation.map.fence.name-max}")
    private String name;

    /** 围栏类型：CIRCLE / POLYGON */
    @NotBlank(message = "{validation.map.fence.type-required}")
    private String fenceType;

    /** 绑定业务类型（可空，空表示通用围栏） */
    @Size(max = 32, message = "{validation.map.fence.biz-type-max}")
    private String bizType;

    /** 绑定业务对象ID（可空） */
    @Size(max = 64, message = "{validation.map.fence.biz-id-max}")
    private String bizId;

    /** 圆心经度（CIRCLE 必填） */
    @DecimalMin(value = "-180", message = "{validation.map.fence.center-lng-min}")
    @DecimalMax(value = "180", message = "{validation.map.fence.center-lng-max}")
    private Double centerLng;

    /** 圆心纬度（CIRCLE 必填） */
    @DecimalMin(value = "-90", message = "{validation.map.fence.center-lat-min}")
    @DecimalMax(value = "90", message = "{validation.map.fence.center-lat-max}")
    private Double centerLat;

    /** 半径（米，CIRCLE 必填） */
    private Double radiusMeters;

    /** 多边形顶点（POLYGON 必填，至少 3 个） */
    private List<FencePointRequest> points;

    /** 坐标系（可空，缺省按对外展示坐标系） */
    @Size(max = 8, message = "{validation.map.fence.coord-type-max}")
    private String coordType;

    /** 是否启用（可空，缺省启用） */
    private Integer enabled;

    /** 备注 */
    @Size(max = 255, message = "{validation.map.fence.remark-max}")
    private String remark;
}
