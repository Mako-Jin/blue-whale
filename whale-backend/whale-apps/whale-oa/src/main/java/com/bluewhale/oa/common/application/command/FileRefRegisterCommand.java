package com.bluewhale.oa.common.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 文件引用登记命令。
 *
 * @author BlueWhale
 */
@Data
public class FileRefRegisterCommand {

    /** 业务类型 */
    @NotBlank(message = "{validation.oa.file-ref-register.biz-type-required}")
    @Size(max = 64, message = "{validation.oa.file-ref-register.biz-type-size}")
    private String bizType;

    /** 业务单据ID */
    @NotNull(message = "{validation.oa.file-ref-register.biz-id-required}")
    private Long bizId;

    /** 文件ID */
    @NotNull(message = "{validation.oa.file-ref-register.file-id-required}")
    private Long fileId;

    /** 原始文件名 */
    @Size(max = 255, message = "{validation.oa.file-ref-register.file-name-size}")
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** MIME 类型 */
    @Size(max = 128, message = "{validation.oa.file-ref-register.content-type-size}")
    private String contentType;

    /** 上传人用户ID */
    private Long uploadedBy;

    /** 用途 */
    @Size(max = 64, message = "{validation.oa.file-ref-register.purpose-size}")
    private String purpose;
}
