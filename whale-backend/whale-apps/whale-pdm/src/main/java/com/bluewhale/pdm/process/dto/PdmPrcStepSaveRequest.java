package com.bluewhale.pdm.process.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 工艺工序保存请求。
 *
 * @author BlueWhale
 */
@Data
public class PdmPrcStepSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工序序号（缺省取当前最大序号 +1） */
    @Min(value = 1, message = "{validation.pdm.pdm-prc-step.step-no-min}")
    private Integer stepNo;

    /** 工序名称 */
    @NotBlank(message = "{validation.pdm.pdm-prc-step.name-required}")
    @Size(max = 128, message = "{validation.pdm.pdm-prc-step.name-size}")
    private String name;

    /** 工作中心 */
    @Size(max = 128, message = "{validation.pdm.pdm-prc-step.work-center-size}")
    private String workCenter;

    /** 标准工时（小时） */
    @DecimalMin(value = "0", message = "{validation.pdm.pdm-prc-step.standard-hours-min}")
    private BigDecimal standardHours;

    /** 准备工时（小时） */
    @DecimalMin(value = "0", message = "{validation.pdm.pdm-prc-step.setup-hours-min}")
    private BigDecimal setupHours;

    /** 工序说明 */
    @Size(max = 500, message = "{validation.pdm.pdm-prc-step.description-size}")
    private String description;
}
