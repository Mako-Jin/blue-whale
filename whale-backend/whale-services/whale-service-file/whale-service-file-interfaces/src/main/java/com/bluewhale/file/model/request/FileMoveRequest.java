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
    @NotBlank(message = "{validation.file.move.bucket-required}")
    @Size(max = 64, message = "{validation.file.move.bucket-size}")
    private String bucket;
}
