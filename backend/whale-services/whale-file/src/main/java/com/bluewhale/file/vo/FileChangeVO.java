package com.bluewhale.file.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件变更流视图对象（知识库增量采集内网接口契约）。
 *
 * @author BlueWhale
 */
@Data
public class FileChangeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 存储文件名 */
    private String fileName;

    /** 原始文件名 */
    private String originalName;

    /** 对象存储桶名称 */
    private String bucket;

    /** 对象存储键 */
    private String objectKey;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件 MIME 类型 */
    private String contentType;

    /** 文件 MD5 摘要（十六进制小写） */
    private String md5;

    /** 状态：1 正常 / 0 禁用 / 2 归档锁定 */
    private Integer status;

    /** 上传用户 ID */
    private Long uploaderId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间（变更游标字段） */
    private LocalDateTime updateTime;
}
