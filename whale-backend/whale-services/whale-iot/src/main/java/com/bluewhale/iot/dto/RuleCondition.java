package com.bluewhale.iot.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 规则触发条件。
 *
 * <p>语义：属性 {@code identifier} 的取值与 {@code value} 按 {@code op} 比较，
 * <b>连续</b> 满足 {@code durationSec} 秒才触发（{@code 0} 表示即时触发）。
 * 持续时长用于抑制抖动：温度瞬时越过阈值不算，持续超限才算。</p>
 *
 * @author BlueWhale
 */
@Data
public class RuleCondition {

    /** 属性标识符（对齐物模型 property.identifier） */
    @NotBlank(message = "{validation.iot.rule-condition.identifier-required}")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{0,63}$", message = "{validation.iot.rule-condition.identifier-pattern}")
    private String identifier;

    /** 比较操作符：> >= < <= == != */
    @NotBlank(message = "{validation.iot.rule-condition.op-required}")
    @Pattern(regexp = "^(>=|<=|==|!=|>|<)$", message = "{validation.iot.rule-condition.op-pattern}")
    private String op;

    /** 比较阈值 */
    @NotNull(message = "{validation.iot.rule-condition.value-required}")
    @DecimalMin(value = "-1.0E12", message = "{validation.iot.rule-condition.value-range}")
    @DecimalMax(value = "1.0E12", message = "{validation.iot.rule-condition.value-range}")
    private Double value;

    /** 连续满足时长（秒），0 表示即时触发 */
    @Min(value = 0, message = "{validation.iot.rule-condition.duration-min}")
    @Max(value = 86400, message = "{validation.iot.rule-condition.duration-max}")
    private Integer durationSec = 0;
}
