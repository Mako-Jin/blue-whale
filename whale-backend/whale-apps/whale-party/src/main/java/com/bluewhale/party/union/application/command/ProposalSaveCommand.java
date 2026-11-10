package com.bluewhale.party.union.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 职代会提案提交命令。
 *
 * @author BlueWhale
 */
@Data
public class ProposalSaveCommand {

    /** 标题 */
    @NotBlank(message = "提案标题不能为空")
    @Size(max = 128, message = "提案标题不得超过 128 字符")
    private String title;

    /** 内容 */
    @NotBlank(message = "提案内容不能为空")
    @Size(max = 5000, message = "提案内容不得超过 5000 字符")
    private String content;
}
