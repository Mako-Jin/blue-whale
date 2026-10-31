package com.bluewhale.music.application.command;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 版权无争议协议签署命令。
 *
 * @author BlueWhale
 */
@Data
public class AgreementAcceptCommand {

    /** 前端展示的协议版本号，须与服务端当前版本一致 */
    @NotBlank(message = "协议版本不能为空")
    private String agreementVersion;

    /** 是否已勾选同意 */
    @AssertTrue(message = "必须勾选同意《版权无争议协议》")
    private boolean agreed;
}
