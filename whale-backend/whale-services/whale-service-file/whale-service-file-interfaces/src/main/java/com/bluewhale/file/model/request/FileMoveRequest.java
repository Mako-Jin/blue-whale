package com.bluewhale.file.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 文件跨桶移动请求。
 *
 * @author BlueWhale
 */
@Data
public class FileMoveRequest {

    /** 目标桶名称 */
    @NotBlank(message = "目标桶名称不能为空")
    @Size(max = 64, message = "桶名称长度不能超过 64")
    private String bucket;
}
