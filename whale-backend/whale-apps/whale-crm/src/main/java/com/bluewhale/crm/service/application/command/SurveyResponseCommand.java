package com.bluewhale.crm.service.application.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 满意度答卷提交命令（按一次性令牌校验）。
 *
 * @author BlueWhale
 */
@Data
public class SurveyResponseCommand {

    /** 一次性令牌（surveyNo.nonce） */
    @NotBlank
    @Size(max = 128)
    private String token;

    /** 总分（1–5） */
    @NotNull
    @Min(1)
    @Max(5)
    private Integer score;

    /** 客户评论 */
    @Size(max = 2000)
    private String comment;

    /** 响应速度评分（1–5） */
    @Min(1)
    @Max(5)
    private Integer speedScore;

    /** 服务态度评分（1–5） */
    @Min(1)
    @Max(5)
    private Integer attitudeScore;

    /** 专业程度评分（1–5） */
    @Min(1)
    @Max(5)
    private Integer professionalScore;
}
