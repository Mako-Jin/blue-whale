package com.bluewhale.mes.quality.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * SPC 规则保存请求
 *
 * @author BlueWhale
 */
@Data
public class MesSpcRuleSaveRequest {

    /** 规则 ID（编辑时必填） */
    private Long id;

    /** 规则编码（空则自动生成） */
    @Size(max = 64, message = "{validation.mes.mes-spc-rule.rule-code-size}")
    private String ruleCode;

    /** 规则名称 */
    @NotBlank(message = "{validation.mes.mes-spc-rule.rule-name-required}")
    @Size(max = 128, message = "{validation.mes.mes-spc-rule.rule-name-size}")
    private String ruleName;

    /** 关联检验计划项 ID */
    @NotNull(message = "{validation.mes.mes-spc-rule.plan-item-id-required}")
    private Long planItemId;

    /** 工序编码 */
    @Size(max = 64, message = "{validation.mes.mes-spc-rule.operation-code-size}")
    private String operationCode;

    /** 控制图类型：XBAR_R */
    @Pattern(regexp = "XBAR_R", message = "{validation.mes.mes-spc-rule.chart-type-pattern}")
    private String chartType;

    /** 子组大小（2–10） */
    @Min(value = 2, message = "{validation.mes.mes-spc-rule.subgroup-size-min}")
    @Max(value = 10, message = "{validation.mes.mes-spc-rule.subgroup-size-max}")
    private Integer subgroupSize;

    /** 中心线 CL */
    private BigDecimal centerLine;

    /** 均值上控制限 UCL */
    private BigDecimal ucl;

    /** 均值下控制限 LCL */
    private BigDecimal lcl;

    /** 极差上控制限 */
    private BigDecimal rUcl;

    /** 极差下控制限 */
    private BigDecimal rLcl;

    /** 命中处理：WARN / BLOCK */
    @Pattern(regexp = "WARN|BLOCK", message = "{validation.mes.mes-spc-rule.alert-action-pattern}")
    private String alertAction;

    /** 状态：ENABLED / DISABLED */
    @Pattern(regexp = "ENABLED|DISABLED", message = "{validation.mes.mes-spc-rule.status-pattern}")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-spc-rule.remark-size}")
    private String remark;
}
