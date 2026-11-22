package com.bluewhale.pdm.change.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 变更单影响评估请求
 *
 * @author BlueWhale
 */
@Data
public class PdmChgOrderAssessRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 影响评估 */
    @NotBlank(message = "{validation.pdm.pdm-chg-order-assess.impact-assessment-required}")
    @Size(max = 2000, message = "{validation.pdm.pdm-chg-order-assess.impact-assessment-size}")
    private String impactAssessment;

    /** 执行计划 */
    @Size(max = 2000, message = "{validation.pdm.pdm-chg-order-assess.execute-plan-size}")
    private String executePlan;
}
