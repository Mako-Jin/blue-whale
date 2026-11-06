package com.bluewhale.mail.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 邮件附件实体，对应表 {@code mail_attachment}。
 *
 * <p>二进制不落本库：上传到 {@code whale-file} 后只保留 {@code fileId}。
 * 未完成上传的内联资源允许 {@code fileId} 为空，由前端按 {@code contentId} 占位。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mail_attachment")
public class MailAttachment extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属邮件ID */
    private Long messageId;

    /** whale-file 文件ID */
    private Long fileId;

    /** 文件名 */
    private String fileName;

    /** MIME 类型 */
    private String contentType;

    /** 大小（字节） */
    private Long sizeBytes;

    /** 是否内联资源（cid 图片）：1 是 / 0 否 */
    private Integer inlineFlag;

    /** Content-ID */
    private String contentId;
}
