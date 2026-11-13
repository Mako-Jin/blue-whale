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
    @NotBlank(message = "uploadId 不能为空")
    private String uploadId;

    /** 原始文件名 */
    @NotBlank(message = "文件名不能为空")
    private String fileName;

    /** 文件总字节数 */
    @NotNull(message = "文件大小不能为空")
    @Positive(message = "文件大小必须大于 0")
    private Long size;

    /** 目标桶名称；为空时取配置的默认桶 */
    private String bucket;
}
