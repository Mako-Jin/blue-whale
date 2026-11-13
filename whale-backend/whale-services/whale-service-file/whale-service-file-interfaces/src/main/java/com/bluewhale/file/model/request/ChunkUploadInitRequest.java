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
    @NotBlank(message = "文件名不能为空")
    private String fileName;

    /** MIME 类型 */
    private String contentType;

    /** 文件总字节数 */
    @NotNull(message = "文件大小不能为空")
    @Positive(message = "文件大小必须大于 0")
    private Long size;

    /** 目标桶名称；为空时取配置的默认桶 */
    private String bucket;

    /** 续传会话 ID（标准 UUID）；为空时新建会话 */
    @Size(max = 36, message = "uploadId 长度不能超过 36")
    private String uploadId;
}
