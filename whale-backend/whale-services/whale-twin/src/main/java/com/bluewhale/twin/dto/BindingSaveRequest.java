package com.bluewhale.twin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 绑定保存请求。
 *
 * @author BlueWhale
 */
@Data
public class BindingSaveRequest {

    /** 孪生实体ID */
    @Min(value = 1, message = "实体ID不合法")
    private Long entityId;

    /** 数据源类型：iot_device / internal_api */
    @NotBlank(message = "数据源类型不能为空")
    @Pattern(regexp = "^(iot_device|internal_api)$", message = "数据源类型仅支持 iot_device / internal_api")
    private String sourceType;

    /** 绑定键：iot_device 为设备ID（纯数字）；internal_api 为接口路径 */
    @NotBlank(message = "绑定键不能为空")
    @Size(max = 256, message = "绑定键最长 256 字符")
    private String bindKey;

    /**
     * 属性映射 JSON：源字段 → 实体状态键；值可为字符串，或
     * {@code {"stateKey":"color","thresholds":[50,80]}}（前端按阈值变色）。
     */
    @Size(max = 4096, message = "属性映射 JSON 最长 4096 字符")
    private String propertyMap;

    /** 轮询间隔毫秒（1000-3600000） */
    @Min(value = 1000, message = "轮询间隔不能小于 1000ms")
    @Max(value = 3600000, message = "轮询间隔不能超过 1 小时")
    private Integer pollIntervalMs;

    /** 是否启用 */
    @Min(value = 0, message = "enabled 只能为 0 或 1")
    @Max(value = 1, message = "enabled 只能为 0 或 1")
    private Integer enabled;
}
