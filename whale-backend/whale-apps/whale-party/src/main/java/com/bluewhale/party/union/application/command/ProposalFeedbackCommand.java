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
    @NotBlank(message = "{validation.party.proposal-feedback.feedback-required}")
    @Size(max = 500, message = "{validation.party.proposal-feedback.feedback-size}")
    private String feedback;
}
