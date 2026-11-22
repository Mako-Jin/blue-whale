package com.bluewhale.mes.quality.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 检验计划项请求
 *
 * @author BlueWhale
 */
@Data
public class MesInspectionPlanItemRequest {

    /** 检验项 ID（编辑已有项时携带） */
    private Long id;

    /** 检验项序号 */
    @NotNull(message = "{validation.mes.mes-inspection-plan-item.item-no-required}")
    @Min(value = 1, message = "{validation.mes.mes-inspection-plan-item.item-no-min}")
    private Integer itemNo;

    /** 检验项名称 */
    @NotBlank(message = "{validation.mes.mes-inspection-plan-item.item-name-required}")
    @Size(max = 128, message = "{validation.mes.mes-inspection-plan-item.item-name-size}")
    private String itemName;

    /** 检验方式：VISUAL / MEASURE / FUNCTION / TEST */
    @Pattern(regexp = "VISUAL|MEASURE|FUNCTION|TEST", message = "{validation.mes.mes-inspection-plan-item.inspection-method-pattern}")
    private String inspectionMethod;

    /** 标准描述 */
    @Size(max = 255, message = "{validation.mes.mes-inspection-plan-item.standard-text-size}")
    private String standardText;

    /** 下限 */
    private BigDecimal minValue;

    /** 上限 */
    private BigDecimal maxValue;

    /** 计量单位 */
    @Size(max = 16, message = "{validation.mes.mes-inspection-plan-item.unit-size}")
    private String unit;

    /** 是否关键项：0 否 / 1 是 */
    private Integer criticalFlag;

    /** 排序号 */
    private Integer sortNo;
}
