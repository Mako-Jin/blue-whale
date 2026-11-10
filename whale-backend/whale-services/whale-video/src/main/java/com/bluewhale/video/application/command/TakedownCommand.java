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
    @NotBlank(message = "版权下架原因不能为空")
    @Size(max = 500, message = "版权下架原因不能超过 500 个字符")
    private String reason;
}
