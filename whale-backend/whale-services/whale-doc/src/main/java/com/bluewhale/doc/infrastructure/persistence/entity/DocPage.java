package com.bluewhale.doc.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 文档页面实体，对应表 {@code doc_page}。
 *
 * <p>{@code contentJson} 为 TipTap / ProseMirror JSON；{@code contentText} 由服务端提取，
 * 不接受前端提交值作为权威数据。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("doc_page")
public class DocPage extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 空间ID */
    private Long spaceId;

    /** 父页面ID，0 表示根节点 */
    private Long parentId;

    /** 页面标题 */
    private String title;

    /** 页面路径（空间内唯一） */
    private String slug;

    /** 节点类型：PAGE / FOLDER */
    private String pageType;

    /** 正文 JSON */
    private String contentJson;

    /** 服务端提取的纯文本投影 */
    private String contentText;

    /** 状态：DRAFT / PUBLISHED / ARCHIVED / PENDING_APPROVAL */
    private String status;

    /** 乐观锁版本 */
    private Long revision;

    /** 最近发布时的 revision */
    private Long publishedRevision;

    /** 同级排序 */
    private Long sortNo;

    /** 图标标识 */
    private String icon;

    /** 创建人ID */
    private Long authorId;

    /** 最近编辑人ID */
    private Long lastEditorId;

    /** 最近发布时间 */
    private LocalDateTime publishedAt;

    /** 归档时间 */
    private LocalDateTime archivedAt;

    /** 移入回收站时间（M2） */
    private LocalDateTime deletedAt;

    /** 移入回收站操作人ID（M2） */
    private Long deletedBy;

    /** 回收站根页面ID，根页面等于自身 {@code id}（M2） */
    private Long trashRootId;

    /** 发布审批流程实例ID（M3） */
    private Long approvalInstanceId;

    /** 审批状态：PENDING / APPROVED / REJECTED（M3） */
    private String approvalStatus;

    /** 审批提交时间（M3） */
    private LocalDateTime approvalSubmittedAt;

    /** 档案条目ID（M3 归档快照） */
    private String archiveItemId;

    /** 归档快照文件ID（M3） */
    private Long archiveFileId;

    /** 归档入档时间（M3） */
    private LocalDateTime archiveAt;

    /** 页面级权限：0 继承空间 / 1 仅 ACL 授权用户可见（M2-4） */
    private Integer isRestricted;
}
