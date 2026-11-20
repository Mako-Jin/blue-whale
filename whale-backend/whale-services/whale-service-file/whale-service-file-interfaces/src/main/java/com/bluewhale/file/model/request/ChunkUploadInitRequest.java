package com.bluewhale.file.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分片上传初始化请求。
 *
 * @author BlueWhale
 */
@Data
public class ChunkUploadInitRequest {

    /** 原始文件名 */
    @NotBlank(message = "{validation.file.common.file-name-required}")
    private String fileName;

    /** MIME 类型 */
    private String contentType;

    /** 文件总字节数 */
    @NotNull(message = "{validation.file.common.size-required}")
    @Positive(message = "{validation.file.common.size-positive}")
    private Long size;

    /** 目标桶名称；为空时取配置的默认桶 */
    private String bucket;

    /** 续传会话 ID（标准 UUID）；为空时新建会话 */
    @Size(max = 36, message = "{validation.file.chunk.upload-id-size}")
    private String uploadId;
}
