package com.bluewhale.file.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 分片上传完成请求。
 *
 * @author BlueWhale
 */
@Data
public class ChunkUploadCompleteRequest {

    /** 上传会话 ID */
    @NotBlank(message = "{validation.file.chunk.upload-id-required}")
    private String uploadId;

    /** 原始文件名 */
    @NotBlank(message = "{validation.file.common.file-name-required}")
    private String fileName;

    /** 文件总字节数 */
    @NotNull(message = "{validation.file.common.size-required}")
    @Positive(message = "{validation.file.common.size-positive}")
    private Long size;

    /** 目标桶名称；为空时取配置的默认桶 */
    private String bucket;
}
