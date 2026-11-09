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
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 128, message = "规则名称不能超过 128 字")
    private String name;

    /** 限定产品ID（可空，空表示全部产品） */
    private Long productId;

    /** 限定设备ID（可空，空表示产品下全部设备） */
    private Long deviceId;

    /** 触发条件 */
    @Valid
    @NotNull(message = "触发条件不能为空")
    private RuleCondition condition;

    /** 动作类型：alert（默认）/ webhook */
    @Pattern(regexp = "^(alert|webhook)$", message = "动作类型仅支持 alert/webhook")
    private String actionType = "alert";

    /** 动作配置（可空，缺省告警级别 WARN、静默期取全局值） */
    @Valid
    private RuleActionConfig actionConfig;

    /** 是否启用（默认启用） */
    private Boolean enabled = Boolean.TRUE;
}
