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
    @NotBlank(message = "{validation.file.common.file-name-required}")
    private String fileName;

    /** 文件 MD5（32 位十六进制） */
    @NotBlank(message = "{validation.file.rapid.md5-required}")
    @Pattern(regexp = "[a-fA-F0-9]{32}", message = "{validation.file.rapid.md5-pattern}")
    private String md5;

    /** 文件字节数 */
    @NotNull(message = "{validation.file.common.size-required}")
    @Positive(message = "{validation.file.common.size-positive}")
    private Long size;

    /** MIME 类型 */
    private String contentType;
}
