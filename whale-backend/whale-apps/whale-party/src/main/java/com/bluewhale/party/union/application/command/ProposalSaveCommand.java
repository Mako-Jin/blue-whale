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
    @NotBlank(message = "{validation.party.proposal.title-required}")
    @Size(max = 128, message = "{validation.party.proposal.title-size}")
    private String title;

    /** 内容 */
    @NotBlank(message = "{validation.party.proposal.content-required}")
    @Size(max = 5000, message = "{validation.party.proposal.content-size}")
    private String content;
}
