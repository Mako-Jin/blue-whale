package com.bluewhale.party.content.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 党建内容退回命令。
 *
 * <p>意见<b>必填</b>：退回不写理由，作者只能改猜，审核也失去可追溯性。</p>
 *
 * @author BlueWhale
 */
@Data
public class ContentRejectCommand {

    /** 审核意见 */
    @NotBlank(message = "{validation.party.content-reject.opinion-required}")
    @Size(max = 1000, message = "{validation.party.content-reject.opinion-size}")
    private String opinion;
}
