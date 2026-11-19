package com.bluewhale.mail.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 附件引用：二进制已在 {@code whale-file}，此处只传引用与元数据。
 *
 * @author BlueWhale
 */
@Data
public class MailAttachmentRequest {

    /** whale-file 文件ID */
    @NotNull(message = "{validation.mail.attachment.file-id-required}")
    private Long fileId;

    /** 文件名 */
    @NotBlank(message = "{validation.mail.attachment.file-name-required}")
    @Size(max = 255, message = "{validation.mail.attachment.file-name-size}")
    private String fileName;

    /** MIME 类型 */
    @Size(max = 128, message = "{validation.mail.attachment.content-type-size}")
    private String contentType;

    /** 大小（字节） */
    private Long sizeBytes;
}
