package com.bluewhale.oa.document.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公文附件（引用 whale-file）。
 *
 * @author BlueWhale
 */
@Data
public class DocAttachment {

    /** 主键ID */
    private Long id;

    /** 单据类型：OUTGOING / INCOMING */
    private String docType;

    /** 单据ID */
    private Long docId;

    /** whale-file 文件ID */
    private Long fileId;

    /** 文件名快照 */
    private String fileName;

    /** 文件字节数快照 */
    private Long fileSize;

    /** MIME 类型快照 */
    private String contentType;

    /** 排序号 */
    private Integer sortNo;

    /** 创建时间 */
    private LocalDateTime createTime;
}
