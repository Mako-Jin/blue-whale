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
    @NotNull(message = "附件文件ID不能为空")
    private Long fileId;

    /** 文件名 */
    @NotBlank(message = "附件文件名不能为空")
    @Size(max = 255, message = "附件文件名过长")
    private String fileName;

    /** MIME 类型 */
    @Size(max = 128, message = "MIME 类型过长")
    private String contentType;

    /** 大小（字节） */
    private Long sizeBytes;
}
