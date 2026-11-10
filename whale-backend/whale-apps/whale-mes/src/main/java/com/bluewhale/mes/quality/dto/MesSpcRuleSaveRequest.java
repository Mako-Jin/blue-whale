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
    @Size(max = 64, message = "规则编码长度不能超过 64")
    private String ruleCode;

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 128, message = "规则名称长度不能超过 128")
    private String ruleName;

    /** 关联检验计划项 ID */
    @NotNull(message = "检验计划项 ID 不能为空")
    private Long planItemId;

    /** 工序编码 */
    @Size(max = 64, message = "工序编码长度不能超过 64")
    private String operationCode;

    /** 控制图类型：XBAR_R */
    @Pattern(regexp = "XBAR_R", message = "控制图类型仅支持 XBAR_R")
    private String chartType;

    /** 子组大小（2–10） */
    @Min(value = 2, message = "子组大小不能小于 2")
    @Max(value = 10, message = "子组大小不能大于 10")
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
    @Pattern(regexp = "WARN|BLOCK", message = "命中处理仅支持 WARN / BLOCK")
    private String alertAction;

    /** 状态：ENABLED / DISABLED */
    @Pattern(regexp = "ENABLED|DISABLED", message = "状态仅支持 ENABLED / DISABLED")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
