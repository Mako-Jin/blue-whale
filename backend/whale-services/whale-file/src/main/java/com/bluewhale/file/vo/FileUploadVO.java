package com.bluewhale.file.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 文件上传结果视图对象（字段名为冻结的 JSON 契约，不得改名）。
 *
 * @author BlueWhale
 */
@Data
public class FileUploadVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 存储文件名（系统生成的唯一名称） */
    private String fileName;

    /** 原始文件名 */
    private String originalName;

    /** 对象存储桶名称 */
    private String bucket;

    /** 对象存储键（桶内完整路径） */
    private String objectKey;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件 MIME 类型 */
    private String contentType;

    /** 文件 MD5 摘要（十六进制小写） */
    private String md5;
}
