package com.bluewhale.video.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 版权下架命令。
 *
 * @author BlueWhale
 */
@Data
public class TakedownCommand {

    /** 版权下架原因（必填并留痕） */
    @NotBlank(message = "{validation.video.takedown.reason-required}")
    @Size(max = 500, message = "{validation.video.takedown.reason-size}")
    private String reason;
}
