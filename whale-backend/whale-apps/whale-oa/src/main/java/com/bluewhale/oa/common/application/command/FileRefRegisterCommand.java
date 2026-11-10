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
    @NotBlank(message = "业务类型不能为空")
    @Size(max = 64, message = "业务类型长度不能超过 64")
    private String bizType;

    /** 业务单据ID */
    @NotNull(message = "业务单据ID不能为空")
    private Long bizId;

    /** 文件ID */
    @NotNull(message = "文件ID不能为空")
    private Long fileId;

    /** 原始文件名 */
    @Size(max = 255, message = "文件名长度不能超过 255")
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** MIME 类型 */
    @Size(max = 128, message = "MIME 类型长度不能超过 128")
    private String contentType;

    /** 上传人用户ID */
    private Long uploadedBy;

    /** 用途 */
    @Size(max = 64, message = "用途长度不能超过 64")
    private String purpose;
}
