package com.bluewhale.pm.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 工作项附件登记请求（仅登记 whale-file 的 file_id 引用）
 *
 * @author BlueWhale
 */
@Data
public class WorkItemAttachmentSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文件ID */
    @NotNull(message = "{validation.pm.work-item-attachment.file-id-required}")
    private Long fileId;

    /** 文件名 */
    @NotBlank(message = "{validation.pm.work-item-attachment.file-name-required}")
    @Size(max = 255, message = "{validation.pm.work-item-attachment.file-name-size}")
    private String fileName;
}
