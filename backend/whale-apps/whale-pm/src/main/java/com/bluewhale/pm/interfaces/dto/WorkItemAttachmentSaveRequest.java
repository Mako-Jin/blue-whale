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
    @NotNull(message = "文件ID不能为空")
    private Long fileId;

    /** 文件名 */
    @NotBlank(message = "文件名不能为空")
    @Size(max = 255, message = "文件名长度不能超过 255")
    private String fileName;
}
