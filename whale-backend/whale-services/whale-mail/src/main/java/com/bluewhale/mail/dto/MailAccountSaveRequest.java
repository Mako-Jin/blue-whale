package com.bluewhale.mail.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 邮箱账号绑定/更新请求。
 *
 * <p>不接收密码字段：凭据以 {@code secretRef} 引用 Nacos 配置键或环境变量名，
 * 由部署侧注入，避免密码经 HTTP 传输并落库。</p>
 *
 * @author BlueWhale
 */
@Data
public class MailAccountSaveRequest {

    /** 邮箱地址 */
    @NotBlank(message = "邮箱地址不能为空")
    @Email(message = "邮箱地址格式不正确")
    @Size(max = 190, message = "邮箱地址过长")
    private String email;

    /** 发件显示名 */
    @Size(max = 96, message = "显示名过长")
    private String displayName;

    /** 账号类型：PERSONAL / SHARED */
    private String accountType;

    /** 凭据引用（Nacos 配置键或环境变量名） */
    @Size(max = 190, message = "凭据引用过长")
    private String secretRef;

    /** IMAP 主机（空则取全局默认） */
    @Size(max = 128, message = "IMAP 主机过长")
    private String imapHost;

    /** IMAP 端口（空则取全局默认） */
    private Integer imapPort;

    /** IMAP 是否 SSL */
    private Boolean imapSsl;

    /** SMTP 主机（空则取全局默认） */
    @Size(max = 128, message = "SMTP 主机过长")
    private String smtpHost;

    /** SMTP 端口（空则取全局默认） */
    private Integer smtpPort;

    /** SMTP 是否 SSL */
    private Boolean smtpSsl;

    /** 是否参与定时同步 */
    private Boolean syncEnabled;
}
