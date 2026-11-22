package com.bluewhale.lms.exam.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 题目选项命令。
 *
 * @author BlueWhale
 */
@Data
public class QuestionOptionCommand {

    /** 选项键（A/B/C…） */
    @NotBlank(message = "{validation.lms.question-option.option-key-required}")
    @Size(max = 8, message = "{validation.lms.question-option.option-key-size}")
    private String optionKey;

    /** 选项内容 */
    @NotBlank(message = "{validation.lms.question-option.content-required}")
    @Size(max = 500, message = "{validation.lms.question-option.content-size}")
    private String content;

    /** 排序号 */
    private Integer sortNo;
}
