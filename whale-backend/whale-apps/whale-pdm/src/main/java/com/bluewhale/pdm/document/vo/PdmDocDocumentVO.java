package com.bluewhale.pdm.document.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 图文档视图
 *
 * @author BlueWhale
 */
@Data
public class PdmDocDocumentVO {

    /** 文档ID */
    private Long id;

    /** 所属目录 */
    private Long folderId;

    /** 文档编号 */
    private String docNo;

    /** 文档标题 */
    private String title;

    /** 文档类型 */
    private String docType;

    /** 关联零件号 */
    private String partNo;

    /** 文档状态 */
    private String status;

    /** 创建人/设计人 */
    private Long ownerId;

    /** 创建人/设计人姓名快照 */
    private String ownerName;

    /** 说明 */
    private String description;

    /** 标签 */
    private String tags;

    /** 最新版本ID（含草稿） */
    private Long latestVersionId;

    /** 最新版本号 */
    private String latestVersionNo;

    /** 当前正式版本ID */
    private Long currentVersionId;

    /** 检出人 */
    private Long checkedOutBy;

    /** 检出时间 */
    private LocalDateTime checkedOutTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
