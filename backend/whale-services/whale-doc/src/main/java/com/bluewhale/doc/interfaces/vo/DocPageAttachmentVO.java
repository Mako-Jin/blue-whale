package com.bluewhale.doc.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 页面附件视图。
 *
 * @author BlueWhale
 */
@Data
public class DocPageAttachmentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 引用记录ID */
    private Long id;

    /** 文件ID */
    private Long fileId;

    /** 原始文件名 */
    private String originalName;

    /** MIME 类型 */
    private String contentType;

    /** 文件字节数 */
    private Long fileSize;

    /** 用途 */
    private String usageType;

    /** 下载/预览地址 */
    private String contentUrl;

    /** 上传时间 */
    private LocalDateTime createTime;
}
