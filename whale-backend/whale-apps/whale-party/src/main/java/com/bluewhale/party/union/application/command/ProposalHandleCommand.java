package com.bluewhale.party.union.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 职代会提案办理命令（立案时可只传办理人，办理时必须传）。
 *
 * @author BlueWhale
 */
@Data
public class ProposalHandleCommand {

    /** 办理人 */
    @NotBlank(message = "{validation.party.proposal-handle.handler-required}")
    @Size(max = 64, message = "{validation.party.proposal-handle.handler-size}")
    private String handler;
}
