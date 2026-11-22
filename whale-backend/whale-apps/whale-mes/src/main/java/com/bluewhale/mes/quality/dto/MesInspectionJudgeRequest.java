package com.bluewhale.mes.quality.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 检验判定请求
 *
 * @author BlueWhale
 */
@Data
public class MesInspectionJudgeRequest {

    /** 判定人 */
    @Size(max = 64, message = "{validation.mes.mes-inspection-judge.judge-size}")
    private String judge;

    /** 判定结论 */
    @NotBlank(message = "{validation.mes.mes-inspection-judge.conclusion-required}")
    @Size(max = 255, message = "{validation.mes.mes-inspection-judge.conclusion-size}")
    private String conclusion;
}
