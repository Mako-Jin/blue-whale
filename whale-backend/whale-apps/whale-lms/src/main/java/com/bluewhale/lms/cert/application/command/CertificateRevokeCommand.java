package com.bluewhale.lms.cert.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 撤销证书命令。
 *
 * @author BlueWhale
 */
@Data
public class CertificateRevokeCommand {

    /** 撤销原因（必填，作为合规留痕） */
    @NotBlank(message = "{validation.lms.certificate-revoke.reason-required}")
    @Size(max = 500, message = "{validation.lms.certificate-revoke.reason-size}")
    private String reason;
}
