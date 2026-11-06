package com.bluewhale.party.union.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 职代会提案反馈命令。
 *
 * @author BlueWhale
 */
@Data
public class ProposalFeedbackCommand {

    /** 反馈内容 */
    @NotBlank(message = "反馈内容不能为空")
    @Size(max = 500, message = "反馈内容不得超过 500 字符")
    private String feedback;
}
