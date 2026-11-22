package com.bluewhale.lms.exam.application.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 单题作答（保存答案用）。
 *
 * <p>{@code answer} 允许为空：学员可以主动清空已填答案。多选题用逗号分隔，
 * 次序与服务端归一无关——归一后按选项键升序比较，填 {@code "C,A"} 与
 * {@code "A,C"} 等价。</p>
 *
 * @author BlueWhale
 */
@Data
public class AttemptAnswerCommand {

    /** 题目 ID */
    @NotNull(message = "{validation.lms.attempt-answer.question-id-required}")
    private Long questionId;

    /** 学员作答 */
    @Size(max = 2000, message = "{validation.lms.attempt-answer.answer-size}")
    private String answer;
}
