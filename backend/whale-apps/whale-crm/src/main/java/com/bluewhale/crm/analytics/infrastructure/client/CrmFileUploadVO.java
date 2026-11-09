package com.bluewhale.crm.analytics.infrastructure.client;

import lombok.Data;

import java.io.Serializable;

/**
 * whale-file 上传结果（{@code Result<FileUploadVO>} 中 {@code data} 的本地镜像）。
 *
 * <p>字段名对应 whale-file 冻结的 JSON 契约（{@code POST /file/internal/upload}），
 * 本服务不依赖 whale-file 模块，故在此本地镜像；导出只需要 {@code id} 与 {@code fileName}。</p>
 *
 * @author BlueWhale
 */
@Data
public class CrmFileUploadVO implements Serializable {

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
