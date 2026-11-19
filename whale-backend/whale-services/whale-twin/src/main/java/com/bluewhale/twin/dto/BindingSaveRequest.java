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
    @Min(value = 1, message = "{validation.twin.binding.entity-id-min}")
    private Long entityId;

    /** 数据源类型：iot_device / internal_api */
    @NotBlank(message = "{validation.twin.binding.source-type-required}")
    @Pattern(regexp = "^(iot_device|internal_api)$", message = "{validation.twin.binding.source-type-pattern}")
    private String sourceType;

    /** 绑定键：iot_device 为设备ID（纯数字）；internal_api 为接口路径 */
    @NotBlank(message = "{validation.twin.binding.bind-key-required}")
    @Size(max = 256, message = "{validation.twin.binding.bind-key-size}")
    private String bindKey;

    /**
     * 属性映射 JSON：源字段 → 实体状态键；值可为字符串，或
     * {@code {"stateKey":"color","thresholds":[50,80]}}（前端按阈值变色）。
     */
    @Size(max = 4096, message = "{validation.twin.binding.property-map-size}")
    private String propertyMap;

    /** 轮询间隔毫秒（1000-3600000） */
    @Min(value = 1000, message = "{validation.twin.binding.poll-min}")
    @Max(value = 3600000, message = "{validation.twin.binding.poll-max}")
    private Integer pollIntervalMs;

    /** 是否启用 */
    @Min(value = 0, message = "{validation.twin.binding.enabled-range}")
    @Max(value = 1, message = "{validation.twin.binding.enabled-range}")
    private Integer enabled;
}
