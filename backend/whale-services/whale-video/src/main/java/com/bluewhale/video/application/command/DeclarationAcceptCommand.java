package com.bluewhale.video.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 上传声明签署命令。
 *
 * @author BlueWhale
 */
@Data
public class DeclarationAcceptCommand {

    /** 声明版本（需与服务端当前版本一致） */
    @NotBlank(message = "声明版本不能为空")
    private String version;

    /** 内容来源：ORIGINAL / AUTHORIZED / ROYALTY_FREE */
    @NotBlank(message = "请声明内容来源")
    private String contentSource;
}
