package com.bluewhale.mail.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 草稿视图。
 *
 * @author BlueWhale
 */
@Data
public class MailDraftVO {

    /** 草稿ID */
    private Long id;

    /** 所属邮箱账号ID */
    private Long accountId;

    /** 客户端草稿键 */
    private String draftKey;

    /** 主题 */
    private String subject;

    /** 收件人 */
    private String toAddrs;

    /** 抄送 */
    private String ccAddrs;

    /** 密送 */
    private String bccAddrs;

    /** HTML 正文 */
    private String bodyHtml;

    /** 纯文本正文 */
    private String bodyText;

    /** 附件 */
    private List<MailAttachmentRequestView> attachments;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 草稿附件视图（不含二进制）
     */
    @Data
    public static class MailAttachmentRequestView {

        /** 文件ID */
        private Long fileId;

        /** 文件名 */
        private String fileName;

        /** MIME 类型 */
        private String contentType;
    }
}
