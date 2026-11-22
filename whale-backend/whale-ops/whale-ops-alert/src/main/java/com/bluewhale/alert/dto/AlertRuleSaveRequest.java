package com.bluewhale.alert.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 告警规则新增 / 更新请求
 *
 * @author BlueWhale
 */
@Data
public class AlertRuleSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 规则名称 */
    @NotBlank(message = "{validation.alert.alert-rule.rule-name-required}")
    @Size(max = 128, message = "{validation.alert.alert-rule.rule-name-size}")
    private String ruleName;

    /** 规则编码（业务唯一） */
    @NotBlank(message = "{validation.alert.alert-rule.rule-code-required}")
    @Size(max = 64, message = "{validation.alert.alert-rule.rule-code-size}")
    private String ruleCode;

    /** 监控指标，如 cpu.usage */
    @Size(max = 64, message = "{validation.alert.alert-rule.metric-size}")
    private String metric;

    /** 监控目标（服务名 / 实例 / 资源标识） */
    @Size(max = 128, message = "{validation.alert.alert-rule.target-size}")
    private String target;

    /** 比较运算符：GT / GE / LT / LE / EQ / NE */
    @Size(max = 16, message = "{validation.alert.alert-rule.operator-size}")
    private String operator;

    /** 触发阈值 */
    private BigDecimal threshold;

    /** 持续时长（秒） */
    private Integer durationSeconds;

    /** 告警级别：INFO / WARN / ERROR / CRITICAL */
    @Size(max = 16, message = "{validation.alert.alert-rule.level-size}")
    private String level;

    /** 通知通道：EMAIL / SMS / WEBHOOK / NONE */
    @Size(max = 32, message = "{validation.alert.alert-rule.channel-size}")
    private String channel;

    /** 规则级接收人（逗号分隔），为空时使用全局配置 */
    @Size(max = 512, message = "{validation.alert.alert-rule.notify-targets-size}")
    private String notifyTargets;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 备注 */
    @Size(max = 255, message = "{validation.alert.alert-rule.remark-size}")
    private String remark;
}
