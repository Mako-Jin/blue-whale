package com.bluewhale.lms.exam.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 手工加题命令。
 *
 * @author BlueWhale
 */
@Data
public class PaperQuestionAddCommand {

    /** 题目 ID */
    @NotNull(message = "题目 ID 不能为空")
    private Long questionId;

    /** 本题分值（为空取题目默认分值） */
    @DecimalMin(value = "0.1", message = "本题分值必须大于 0")
    private BigDecimal score;
}
