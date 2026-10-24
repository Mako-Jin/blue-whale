package com.bluewhale.archive.remote;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 文件元数据（跨服务内网响应体）。
 *
 * @author BlueWhale
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RemoteFileMeta {

    /** 文件ID */
    private Long id;

    /** 存储文件名 */
    private String fileName;

    /** 原始文件名 */
    private String originalName;

    /** 对象存储桶 */
    private String bucket;

    /** 对象存储键 */
    private String objectKey;

    /** 文件大小（字节） */
    private Long fileSize;

    /** MIME 类型 */
    private String contentType;

    /** 文件 MD5 */
    private String md5;

    /** 状态：1 正常 / 0 禁用 / 2 归档锁定 */
    private Integer status;
}
