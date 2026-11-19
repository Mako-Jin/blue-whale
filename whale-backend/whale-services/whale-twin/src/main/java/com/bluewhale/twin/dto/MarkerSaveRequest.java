package com.bluewhale.twin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 标注/热点保存请求。
 *
 * @author BlueWhale
 */
@Data
public class MarkerSaveRequest {

    /** 挂靠实体ID（可空表示场景级锚点） */
    @Min(value = 1, message = "{validation.twin.marker.entity-id-min}")
    private Long entityId;

    /** 类型：label 标签 / hotspot 热点 / alarm 告警定位 */
    @NotBlank(message = "{validation.twin.marker.type-required}")
    @Pattern(regexp = "^(label|hotspot|alarm)$", message = "{validation.twin.marker.type-pattern}")
    private String markerType;

    /** 标注内容（文本或 JSON，最长 1024 字符） */
    @Size(max = 1024, message = "{validation.twin.marker.content-size}")
    private String content;

    /** 三维坐标 JSON（x/y/z，前端约定；填了必须是 JSON 对象） */
    @Size(max = 512, message = "{validation.twin.marker.position-size}")
    private String positionJson;

    /** 点击跳转地址（可空） */
    @Size(max = 512, message = "{validation.twin.marker.link-size}")
    private String linkUrl;

    /** 是否启用 */
    @Min(value = 0, message = "{validation.twin.marker.enabled-range}")
    @Max(value = 1, message = "{validation.twin.marker.enabled-range}")
    private Integer enabled;
}
