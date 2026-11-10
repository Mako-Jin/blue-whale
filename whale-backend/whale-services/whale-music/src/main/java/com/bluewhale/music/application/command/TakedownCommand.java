package com.bluewhale.music.application.command;

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

    /** 版权下架原因（用于留痕） */
    @NotBlank(message = "版权下架必须填写原因")
    @Size(max = 500, message = "下架原因最多 500 个字符")
    private String reason;
}
