package com.bluewhale.doc.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 页面详情视图。
 *
 * @author BlueWhale
 */
@Data
public class DocPageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 页面ID */
    private Long id;

    /** 空间ID */
    private Long spaceId;

    /** 空间名称 */
    private String spaceName;

    /** 空间可见性 */
    private String spaceVisibility;

    /** 父页面ID */
    private Long parentId;

    /** 标题 */
    private String title;

    /** 页面路径 */
    private String slug;

    /** 节点类型 */
    private String pageType;

    /** 正文 JSON 字符串 */
    private String contentJson;

    /** 状态 */
    private String status;

    /** 当前 revision */
    private Long revision;

    /** 最近发布 revision */
    private Long publishedRevision;

    /** 排序 */
    private Long sortNo;

    /** 图标 */
    private String icon;

    /** 创建人ID */
    private Long authorId;

    /** 最近编辑人ID */
    private Long lastEditorId;

    /** 发布时间 */
    private LocalDateTime publishedAt;

    /** 归档时间 */
    private LocalDateTime archivedAt;

    /** 审批状态：PENDING / APPROVED / REJECTED（M3） */
    private String approvalStatus;

    /** 审批流程实例ID（M3） */
    private Long approvalInstanceId;

    /** 审批提交时间（M3） */
    private LocalDateTime approvalSubmittedAt;

    /** 档案条目ID（M3） */
    private String archiveItemId;

    /** 归档入档时间（M3） */
    private LocalDateTime archiveAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 是否启用页面级权限（M2-4） */
    private boolean restricted;

    /** 当前用户是否可编辑 */
    private boolean canEdit;

    /** 当前用户是否可评论 */
    private boolean canComment;

    /** 当前用户是否可管理（空间 ADMIN 及以上） */
    private boolean canManage;

    /** 当前用户是否已收藏 */
    private boolean starred;

    /** 面包屑（含当前页） */
    private List<DocBreadcrumbVO> breadcrumb;
}
