package com.bluewhale.job.scheduler.archive;

import lombok.Data;

import java.io.Serializable;

/**
 * 归档上传结果视图对象，对应 whale-file {@code Result<FileUploadVO>} 响应中的 {@code data}。
 *
 * <p>字段名为 whale-file 冻结的 JSON 契约（见 {@code POST /file/internal/upload}），
 * 本服务不依赖 whale-file 模块，故在此本地镜像以便反序列化；归档仅需 {@code bucket}
 * 与 {@code objectKey} 用于回执，其余字段保留完整契约。</p>
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
