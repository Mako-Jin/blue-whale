package com.bluewhale.lms.exam.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 题目状态变更命令。
 *
 * @author BlueWhale
 */
@Data
public class QuestionStatusCommand {

    /** 动作：ACTIVATE（启用）/ DISABLE（停用） */
    @NotBlank(message = "{validation.lms.question-status.action-required}")
    private String action;
}
