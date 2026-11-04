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
    @NotNull(message = "检验项序号不能为空")
    @Min(value = 1, message = "检验项序号必须大于 0")
    private Integer itemNo;

    /** 检验项名称 */
    @NotBlank(message = "检验项名称不能为空")
    @Size(max = 128, message = "检验项名称长度不能超过 128")
    private String itemName;

    /** 检验方式：VISUAL / MEASURE / FUNCTION / TEST */
    @Pattern(regexp = "VISUAL|MEASURE|FUNCTION|TEST", message = "检验方式不合法")
    private String inspectionMethod;

    /** 标准描述 */
    @Size(max = 255, message = "标准描述长度不能超过 255")
    private String standardText;

    /** 下限 */
    private BigDecimal minValue;

    /** 上限 */
    private BigDecimal maxValue;

    /** 计量单位 */
    @Size(max = 16, message = "计量单位长度不能超过 16")
    private String unit;

    /** 是否关键项：0 否 / 1 是 */
    private Integer criticalFlag;

    /** 排序号 */
    private Integer sortNo;
}
