package com.bluewhale.pdm.document.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PDM 图文档主档
 *
 * <p>文档元数据与最新版本引用；文件字节保存在 {@code whale-file}。
 * 文档编号在租户内唯一，已发布版本不可修改。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pdm_doc_document")
public class PdmDocDocument extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属目录 */
    private Long folderId;

    /** 文档编号 */
    private String docNo;

    /** 文档标题 */
    private String title;

    /** 文档类型：DRAWING/DOC/SPEC/REPORT */
    private String docType;

    /** 关联零件号 */
    private String partNo;

    /** 文档状态：DRAFT/RELEASED */
    private String status;

    /** 创建人/设计人 */
    private Long ownerId;

    /** 创建人/设计人姓名快照 */
    private String ownerName;

    /** 说明 */
    private String description;

    /** 标签，逗号分隔 */
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
}
