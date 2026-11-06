package com.bluewhale.mail.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 邮件视图：列表与详情共用。
 *
 * <p>列表接口不填充 {@code bodyHtml}/{@code bodyText}/{@code attachments}，
 * 保持分页响应稳定在 KB 级；详情接口才回源正文与附件，避免一次翻页拉出几十 MB。</p>
 *
 * @author BlueWhale
 */
@Data
public class MailMessageVO {

    /** 邮件ID */
    private Long id;

    /** 所属文件夹ID */
    private Long folderId;

    /** 文件夹编码 */
    private String folderCode;

    /** 主题 */
    private String subject;

    /** 发件人地址 */
    private String fromAddr;

    /** 发件人显示名 */
    private String fromName;

    /** 收件人 */
    private String toAddrs;

    /** 抄送 */
    private String ccAddrs;

    /** 密送 */
    private String bccAddrs;

    /** 发信时间 */
    private LocalDateTime sentTime;

    /** 邮件大小（字节） */
    private Long sizeBytes;

    /** 是否含附件 */
    private Integer hasAttachment;

    /** 已读 */
    private Integer seen;

    /** 星标 */
    private Integer flagged;

    /** 摘要 */
    private String preview;

    /** 纯文本正文（仅详情） */
    private String bodyText;

    /** HTML 正文（仅详情，渲染前须净化） */
    private String bodyHtml;

    /** 附件列表（仅详情） */
    private List<MailAttachmentVO> attachments;
}
