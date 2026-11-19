package com.bluewhale.iot.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 规则保存请求（创建与更新共用）。
 *
 * @author BlueWhale
 */
@Data
public class RuleSaveRequest {

    /** 规则名称 */
    @NotBlank(message = "{validation.iot.rule.name-required}")
    @Size(max = 128, message = "{validation.iot.rule.name-size}")
    private String name;

    /** 限定产品ID（可空，空表示全部产品） */
    private Long productId;

    /** 限定设备ID（可空，空表示产品下全部设备） */
    private Long deviceId;

    /** 触发条件 */
    @Valid
    @NotNull(message = "{validation.iot.rule.condition-required}")
    private RuleCondition condition;

    /** 动作类型：alert（默认）/ webhook */
    @Pattern(regexp = "^(alert|webhook)$", message = "{validation.iot.rule.action-type-pattern}")
    private String actionType = "alert";

    /** 动作配置（可空，缺省告警级别 WARN、静默期取全局值） */
    @Valid
    private RuleActionConfig actionConfig;

    /** 是否启用（默认启用） */
    private Boolean enabled = Boolean.TRUE;
}
