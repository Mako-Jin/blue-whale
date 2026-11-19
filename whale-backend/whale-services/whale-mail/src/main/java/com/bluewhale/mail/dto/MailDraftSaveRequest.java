package com.bluewhale.mail.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 草稿保存请求。
 *
 * @author BlueWhale
 */
@Data
public class MailDraftSaveRequest {

    /** 所属邮箱账号ID */
    @NotNull(message = "{validation.mail.draft.account-required}")
    private Long accountId;

    /** 客户端草稿键（用于本地草稿合并） */
    @Size(max = 64, message = "{validation.mail.draft.draft-key-size}")
    private String draftKey;

    /** 主题 */
    @Size(max = 512, message = "{validation.mail.draft.subject-size}")
    private String subject;

    /** 收件人 */
    private List<String> to;

    /** 抄送 */
    private List<String> cc;

    /** 密送 */
    private List<String> bcc;

    /** HTML 正文 */
    private String bodyHtml;

    /** 纯文本正文 */
    private String bodyText;

    /** 附件草稿 */
    private List<MailAttachmentRequest> attachments;
}
