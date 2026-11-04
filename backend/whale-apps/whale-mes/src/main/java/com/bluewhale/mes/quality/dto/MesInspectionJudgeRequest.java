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
    @Size(max = 64, message = "判定人长度不能超过 64")
    private String judge;

    /** 判定结论 */
    @NotBlank(message = "判定结论不能为空")
    @Size(max = 255, message = "判定结论长度不能超过 255")
    private String conclusion;
}
