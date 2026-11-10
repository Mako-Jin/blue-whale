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
    @Min(value = 1, message = "实体ID不合法")
    private Long entityId;

    /** 类型：label 标签 / hotspot 热点 / alarm 告警定位 */
    @NotBlank(message = "标注类型不能为空")
    @Pattern(regexp = "^(label|hotspot|alarm)$", message = "标注类型仅支持 label / hotspot / alarm")
    private String markerType;

    /** 标注内容（文本或 JSON，最长 1024 字符） */
    @Size(max = 1024, message = "标注内容最长 1024 字符")
    private String content;

    /** 三维坐标 JSON（x/y/z，前端约定；填了必须是 JSON 对象） */
    @Size(max = 512, message = "坐标 JSON 最长 512 字符")
    private String positionJson;

    /** 点击跳转地址（可空） */
    @Size(max = 512, message = "跳转地址最长 512 字符")
    private String linkUrl;

    /** 是否启用 */
    @Min(value = 0, message = "enabled 只能为 0 或 1")
    @Max(value = 1, message = "enabled 只能为 0 或 1")
    private Integer enabled;
}
