package com.bluewhale.file.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 文件重命名请求。
 *
 * @author BlueWhale
 */
@Data
public class FileRenameRequest {

    /** 新的原始文件名（不含路径） */
    @NotBlank(message = "原始文件名不能为空")
    @Size(max = 255, message = "原始文件名长度不能超过 255")
    private String originalName;
}
