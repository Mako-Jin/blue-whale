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
    @NotBlank(message = "属性标识符不能为空")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{0,63}$", message = "标识符须为字母开头的字母/数字/下划线，1-64 位")
    private String identifier;

    /** 比较操作符：> >= < <= == != */
    @NotBlank(message = "比较操作符不能为空")
    @Pattern(regexp = "^(>=|<=|==|!=|>|<)$", message = "操作符仅支持 > >= < <= == !=")
    private String op;

    /** 比较阈值 */
    @NotNull(message = "阈值不能为空")
    @DecimalMin(value = "-1.0E12", message = "阈值超出范围")
    @DecimalMax(value = "1.0E12", message = "阈值超出范围")
    private Double value;

    /** 连续满足时长（秒），0 表示即时触发 */
    @Min(value = 0, message = "持续时间不能为负")
    @Max(value = 86400, message = "持续时间不能超过 86400 秒")
    private Integer durationSec = 0;
}
