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
    @NotBlank(message = "选项键不能为空")
    @Size(max = 8, message = "选项键最长 8 字符")
    private String optionKey;

    /** 选项内容 */
    @NotBlank(message = "选项内容不能为空")
    @Size(max = 500, message = "选项内容最长 500 字符")
    private String content;

    /** 排序号 */
    private Integer sortNo;
}
