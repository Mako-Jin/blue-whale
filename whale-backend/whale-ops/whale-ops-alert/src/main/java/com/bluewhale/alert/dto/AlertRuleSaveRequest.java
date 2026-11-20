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
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 128, message = "规则名称长度不能超过 128")
    private String ruleName;

    /** 规则编码（业务唯一） */
    @NotBlank(message = "规则编码不能为空")
    @Size(max = 64, message = "规则编码长度不能超过 64")
    private String ruleCode;

    /** 监控指标，如 cpu.usage */
    @Size(max = 64, message = "监控指标长度不能超过 64")
    private String metric;

    /** 监控目标（服务名 / 实例 / 资源标识） */
    @Size(max = 128, message = "监控目标长度不能超过 128")
    private String target;

    /** 比较运算符：GT / GE / LT / LE / EQ / NE */
    @Size(max = 16, message = "比较运算符长度不能超过 16")
    private String operator;

    /** 触发阈值 */
    private BigDecimal threshold;

    /** 持续时长（秒） */
    private Integer durationSeconds;

    /** 告警级别：INFO / WARN / ERROR / CRITICAL */
    @Size(max = 16, message = "告警级别长度不能超过 16")
    private String level;

    /** 通知通道：EMAIL / SMS / WEBHOOK / NONE */
    @Size(max = 32, message = "通知通道长度不能超过 32")
    private String channel;

    /** 规则级接收人（逗号分隔），为空时使用全局配置 */
    @Size(max = 512, message = "规则级接收人长度不能超过 512")
    private String notifyTargets;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
