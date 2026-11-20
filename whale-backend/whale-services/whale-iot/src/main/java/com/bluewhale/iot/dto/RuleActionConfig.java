package com.bluewhale.iot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * 规则动作配置（存 {@code iot_rule.action_config}）。
 *
 * @author BlueWhale
 */
@Data
public class RuleActionConfig {

    /** 告警级别：INFO / WARN / ERROR / CRITICAL（默认 WARN） */
    @Pattern(regexp = "^(INFO|WARN|ERROR|CRITICAL)$", message = "{validation.iot.rule-action.level-pattern}")
    private String level = "WARN";

    /** 静默期（秒）：同规则在该窗口内不重复触发；缺省取全局配置 */
    @Min(value = 0, message = "{validation.iot.rule-action.silence-min}")
    @Max(value = 86400, message = "{validation.iot.rule-action.silence-max}")
    private Integer silenceSeconds;

    /** 通知渠道提示（透传给告警内容，实际渠道由 whale-alert 决定） */
    private List<String> notifyChannels;
}
