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
    @NotBlank(message = "{validation.video.declaration.version-required}")
    private String version;

    /** 内容来源：ORIGINAL / AUTHORIZED / ROYALTY_FREE */
    @NotBlank(message = "{validation.video.declaration.content-source-required}")
    private String contentSource;
}
