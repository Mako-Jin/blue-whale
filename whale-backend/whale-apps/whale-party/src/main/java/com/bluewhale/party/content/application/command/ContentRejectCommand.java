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
    @NotBlank(message = "退回必须填写审核意见")
    @Size(max = 1000, message = "审核意见不得超过 1000 字符")
    private String opinion;
}
