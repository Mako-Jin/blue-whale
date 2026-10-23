package com.bluewhale.file.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @Size(max = 255, message = "文件名长度不能超过 255")
    private String fileName;

    /** 文件 MIME 类型，可选 */
    @Size(max = 128, message = "MIME 类型长度不能超过 128")
    private String contentType;

    /** 文件总字节数 */
    @NotNull(message = "文件大小不能为空")
    @Min(value = 1, message = "文件大小必须大于 0")
    private Long size;

    /** 目标桶名称，可选；为空时取配置的默认桶 */
    @Size(max = 64, message = "桶名称长度不能超过 64")
    private String bucket;

    /** 续传时传入既有上传会话 ID，可选；为空时新建会话 */
    @Size(max = 64, message = "uploadId 长度不能超过 64")
    private String uploadId;
}
