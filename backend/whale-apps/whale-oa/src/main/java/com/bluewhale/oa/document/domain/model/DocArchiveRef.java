package com.bluewhale.oa.document.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公文归档映射。
 *
 * @author BlueWhale
 */
@Data
public class DocArchiveRef {

    /** 主键ID */
    private Long id;

    /** 单据类型：OUTGOING / INCOMING */
    private String docType;

    /** 单据ID */
    private Long docId;

    /** 档案来源类型 */
    private String sourceType;

    /** 档案来源业务ID */
    private String sourceId;

    /** 档案条目ID */
    private String archiveItemId;

    /** 状态：PENDING / ARCHIVED / FAILED */
    private String status;

    /** 失败原因 */
    private String errorMessage;

    /** 推送成功时间 */
    private LocalDateTime pushedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
