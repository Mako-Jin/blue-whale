package com.bluewhale.lms.exam.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 单题判分。
 *
 * @author BlueWhale
 */
@Data
public class GradeItemCommand {

    /** 题目 ID */
    @NotNull(message = "{validation.lms.grade-item.question-id-required}")
    private Long questionId;

    /** 本题得分（0 至该题满分） */
    @NotNull(message = "{validation.lms.grade-item.score-required}")
    @DecimalMin(value = "0.0", message = "{validation.lms.grade-item.score-min}")
    private BigDecimal score;
}
