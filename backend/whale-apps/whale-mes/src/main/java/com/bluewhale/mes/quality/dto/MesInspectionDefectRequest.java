package com.bluewhale.mes.quality.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 检验不良明细请求
 *
 * @author BlueWhale
 */
@Data
public class MesInspectionDefectRequest {

    /** 缺陷代码 */
    @NotBlank(message = "缺陷代码不能为空")
    @Size(max = 64, message = "缺陷代码长度不能超过 64")
    private String defectCode;

    /** 缺陷名称 */
    @Size(max = 128, message = "缺陷名称长度不能超过 128")
    private String defectName;

    /** 不良数量 */
    @DecimalMin(value = "0.0001", message = "不良数量必须大于 0")
    private BigDecimal quantity;

    /** 严重度：MINOR / MAJOR / CRITICAL */
    @Pattern(regexp = "MINOR|MAJOR|CRITICAL", message = "严重度仅支持 MINOR / MAJOR / CRITICAL")
    private String severity;

    /** 不良描述 */
    @Size(max = 255, message = "不良描述长度不能超过 255")
    private String description;
}
