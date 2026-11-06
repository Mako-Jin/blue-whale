package com.bluewhale.lms.cert.application.command;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 换发证书命令。
 *
 * <p>不接收有效期参数：新证书的有效期一律按模板重新起算。让换发可以自定义时长
 * 会绕开模板口径（等于用换发给别人延期），而换发的正当语义只是「复核后重新起算」。</p>
 *
 * @author BlueWhale
 */
@Data
public class CertificateRenewCommand {

    /** 换发备注（复核依据） */
    @Size(max = 500, message = "备注不得超过 500 字符")
    private String remark;
}
