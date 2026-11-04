package com.bluewhale.oa.common.application.view;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件引用视图。
 *
 * <p>不暴露裸 {@code fileId}，前端下载使用 {@code id} 走 OA 代理。</p>
 *
 * @author BlueWhale
 */
@Data
public class FileRefView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文件引用ID（下载代理标识） */
    private Long id;

    /** 业务类型 */
    private String bizType;

    /** 业务单据ID */
    private Long bizId;

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
