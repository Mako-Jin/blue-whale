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
    @NotNull(message = "题目不能为空")
    private Long questionId;

    /** 本题得分（0 至该题满分） */
    @NotNull(message = "本题得分不能为空")
    @DecimalMin(value = "0.0", message = "本题得分不能为负")
    private BigDecimal score;
}
