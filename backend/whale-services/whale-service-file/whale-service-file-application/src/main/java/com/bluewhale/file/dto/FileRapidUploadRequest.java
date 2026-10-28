package com.bluewhale.file.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 秒传（哈希去重）请求：客户端先声明文件 MD5 与大小，命中已有对象时仅登记元数据。
 *
 * @author BlueWhale
 */
@Data
public class FileRapidUploadRequest {

    /** 原始文件名 */
    @NotBlank(message = "文件名不能为空")
    @Size(max = 255, message = "文件名长度不能超过 255")
    private String fileName;

    /** 文件 MD5 摘要（32 位十六进制） */
    @NotBlank(message = "MD5 不能为空")
    @Pattern(regexp = "^[a-fA-F0-9]{32}$", message = "MD5 格式非法")
    private String md5;

    /** 文件总字节数 */
    @NotNull(message = "文件大小不能为空")
    @Min(value = 1, message = "文件大小必须大于 0")
    private Long size;

    /** 文件 MIME 类型，可选；为空时沿用命中记录的 MIME 类型 */
    @Size(max = 128, message = "MIME 类型长度不能超过 128")
    private String contentType;
}
