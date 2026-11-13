package com.bluewhale.file.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 秒传请求。
 *
 * @author BlueWhale
 */
@Data
public class FileRapidUploadRequest {

    /** 原始文件名 */
    @NotBlank(message = "文件名不能为空")
    private String fileName;

    /** 文件 MD5（32 位十六进制） */
    @NotBlank(message = "MD5 不能为空")
    @Pattern(regexp = "[a-fA-F0-9]{32}", message = "MD5 必须为 32 位十六进制")
    private String md5;

    /** 文件字节数 */
    @NotNull(message = "文件大小不能为空")
    @Positive(message = "文件大小必须大于 0")
    private Long size;

    /** MIME 类型 */
    private String contentType;
}
