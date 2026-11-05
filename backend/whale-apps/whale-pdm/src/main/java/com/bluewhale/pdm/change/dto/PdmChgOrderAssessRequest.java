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
    @NotBlank(message = "影响评估不能为空")
    @Size(max = 2000, message = "影响评估长度不能超过 2000")
    private String impactAssessment;

    /** 执行计划 */
    @Size(max = 2000, message = "执行计划长度不能超过 2000")
    private String executePlan;
}
