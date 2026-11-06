package com.bluewhale.mail.vo;

import lombok.Data;

/**
 * 邮件附件视图。
 *
 * @author BlueWhale
 */
@Data
public class MailAttachmentVO {

    /** 附件ID */
    private Long id;

    /** whale-file 文件ID */
    private Long fileId;

    /** 文件名 */
    private String fileName;

    /** MIME 类型 */
    private String contentType;

    /** 大小（字节） */
    private Long sizeBytes;

    /** 是否内联资源 */
    private Integer inlineFlag;

    /** Content-ID */
    private String contentId;
}
