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
    @NotBlank(message = "{validation.file.rename.name-required}")
    @Size(max = 255, message = "{validation.file.rename.name-size}")
    private String originalName;
}
