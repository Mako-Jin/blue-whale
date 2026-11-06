package com.bluewhale.mail.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 发信请求。
 *
 * @author BlueWhale
 */
@Data
public class MailSendRequest {

    /** 发信邮箱账号ID */
    @NotNull(message = "发信账号不能为空")
    private Long accountId;

    /** 收件人 */
    @Size(min = 1, message = "至少一个收件人")
    private List<@Email(message = "收件人地址格式不正确") String> to;

    /** 抄送 */
    private List<@Email(message = "抄送地址格式不正确") String> cc;

    /** 密送 */
    private List<@Email(message = "密送地址格式不正确") String> bcc;

    /** 主题 */
    @Size(max = 512, message = "主题过长")
    private String subject;

    /** HTML 正文 */
    private String bodyHtml;

    /** 纯文本正文（HTML 为空时使用） */
    private String bodyText;

    /** 附件（已上传至 whale-file） */
    @Valid
    private List<MailAttachmentRequest> attachments;
}
