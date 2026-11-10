package com.bluewhale.oa.common.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件引用聚合。
 *
 * <p>文件字节由 {@code whale-service-file} 存储，OA 只保存业务归属与展示元数据，
 * 对外不暴露裸 {@code fileId} 下载地址，下载统一走 OA 代理。</p>
 *
 * @author BlueWhale
 */
@Data
public class FileRef {

    /** 主键 */
    private Long id;

    /** 业务类型 */
    private String bizType;

    /** 业务单据ID */
    private Long bizId;

    /** 文件ID */
    private Long fileId;

    /** 原始文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** MIME 类型 */
    private String contentType;

    /** 上传人用户ID */
    private Long uploadedBy;

    /** 用途 */
    private String purpose;

    /** 创建时间 */
    private LocalDateTime createTime;
}
