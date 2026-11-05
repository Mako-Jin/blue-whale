package com.bluewhale.pdm.change.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 变更单新建/修改请求
 *
 * @author BlueWhale
 */
@Data
public class PdmChgOrderSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 来源变更申请ID（新建必填，修改时忽略） */
    private Long ecrId;

    /** 变更标题 */
    @NotBlank(message = "变更标题不能为空")
    @Size(max = 200, message = "变更标题长度不能超过 200")
    private String title;

    /** 影响评估 */
    @Size(max = 2000, message = "影响评估长度不能超过 2000")
    private String impactAssessment;

    /** 执行计划 */
    @Size(max = 2000, message = "执行计划长度不能超过 2000")
    private String executePlan;
}
