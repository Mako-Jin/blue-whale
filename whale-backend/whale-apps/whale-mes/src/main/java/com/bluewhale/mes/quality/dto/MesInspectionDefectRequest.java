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
    @NotBlank(message = "{validation.mes.mes-inspection-defect.defect-code-required}")
    @Size(max = 64, message = "{validation.mes.mes-inspection-defect.defect-code-size}")
    private String defectCode;

    /** 缺陷名称 */
    @Size(max = 128, message = "{validation.mes.mes-inspection-defect.defect-name-size}")
    private String defectName;

    /** 不良数量 */
    @DecimalMin(value = "0.0001", message = "{validation.mes.mes-inspection-defect.quantity-min}")
    private BigDecimal quantity;

    /** 严重度：MINOR / MAJOR / CRITICAL */
    @Pattern(regexp = "MINOR|MAJOR|CRITICAL", message = "{validation.mes.mes-inspection-defect.severity-pattern}")
    private String severity;

    /** 不良描述 */
    @Size(max = 255, message = "{validation.mes.mes-inspection-defect.description-size}")
    private String description;
}
