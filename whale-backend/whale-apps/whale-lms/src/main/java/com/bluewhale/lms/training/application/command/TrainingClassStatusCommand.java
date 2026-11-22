package com.bluewhale.lms.training.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 培训班状态变更命令。
 *
 * @author BlueWhale
 */
@Data
public class TrainingClassStatusCommand {

    /** 目标动作：PUBLISH（发布）/ CLOSE（结课） */
    @NotBlank(message = "{validation.lms.training-class-status.action-required}")
    private String action;
}
