// 文档中心前端类型：与 whale-doc 后端 VO / DTO 字段保持一致

/** 空间可见性 */
export type DocVisibility = 'PUBLIC' | 'MEMBER';

/** 空间成员角色（层级 OWNER > ADMIN > EDITOR > READER） */
export type DocMemberRole = 'OWNER' | 'ADMIN' | 'EDITOR' | 'READER';

/** 页面节点类型 */
export type DocPageType = 'PAGE' | 'FOLDER';

/** 页面状态 */
export type DocPageStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED' | 'PENDING_APPROVAL';

/** 发布审批状态（M3） */
export type DocApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'NONE';

/** 版本快照类型 */
export type DocVersionKind = 'MANUAL' | 'PUBLISH' | 'AUTO' | 'RESTORE';

/** 附件用途 */
export type DocAttachmentUsage = 'ATTACHMENT' | 'INLINE';

/** TipTap / ProseMirror JSON 文档 */
export type DocJson = Record<string, unknown>;

/** 文档空间 */
export interface DocSpace {
  id: number;
  spaceKey: string;
  name: string;
  description?: string | null;
  icon?: string | null;
  visibility: DocVisibility;
  ownerId: number;
  status: number;
  memberRole?: DocMemberRole | null;
  memberCount?: number | null;
  createTime?: string | null;
  updateTime?: string | null;
}

/** 空间成员 */
export interface DocMember {
  id: number;
  spaceId: number;
  userId: number;
  memberRole: DocMemberRole;
  createTime?: string | null;
}

/** 页面面包屑 */
export interface DocBreadcrumb {
  id: number;
  title: string;
}

/** 页面详情 */
export interface DocPage {
  id: number;
  spaceId: number;
  spaceName: string;
  spaceVisibility: DocVisibility;
  parentId: number;
  title: string;
  slug: string;
  pageType: DocPageType;
  /** 正文 JSON 字符串（后端 content_json 原样返回，需解析后交给 TipTap） */
  contentJson?: string | null;
  status: DocPageStatus;
  revision: number;
  publishedRevision?: number | null;
  sortNo: number;
  icon?: string | null;
  authorId: number;
  lastEditorId: number;
  publishedAt?: string | null;
  archivedAt?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
  /** 是否启用页面级权限（M2-4，受限页面仅 ACL 授权用户可见） */
  restricted?: boolean;
  canEdit: boolean;
  canComment: boolean;
  /** 是否可管理页面（空间 ADMIN 及以上） */
  canManage: boolean;
  starred: boolean;
  breadcrumb?: DocBreadcrumb[];
  /** 审批状态（M3） */
  approvalStatus?: DocApprovalStatus | null;
  /** 审批流程实例ID（M3） */
  approvalInstanceId?: number | null;
  /** 审批提交时间（M3） */
  approvalSubmittedAt?: string | null;
  /** 档案条目ID（M3） */
  archiveItemId?: string | null;
  /** 归档入档时间（M3） */
  archiveAt?: string | null;
}

/** 发布审批状态（M3） */
export interface DocApprovalInfo {
  pageId: number;
  pageStatus: DocPageStatus;
  approvalStatus: DocApprovalStatus;
  approvalInstanceId?: number | null;
  submittedAt?: string | null;
  approvalEnabled: boolean;
}

/** 文档模板（M3） */
export interface DocTemplate {
  id: number;
  spaceId: number;
  name: string;
  description?: string | null;
  pageType: DocPageType;
  icon?: string | null;
  contentJson?: string | null;
  status: number;
  sortNo: number;
  authorId?: number | null;
  createTime?: string | null;
  updateTime?: string | null;
}

/** 页面分享记录（M3） */
export interface DocPageShare {
  id: number;
  pageId: number;
  shareToken: string;
  sharePath: string;
  shareRole: DocMemberRole;
  expiresAt?: string | null;
  status: number;
  createdBy: number;
  createTime?: string | null;
  expired: boolean;
}

/** 文档导入结果（M3） */
export interface DocImportResult {
  pageId: number;
  revision: number;
  status: DocPageStatus;
  format: string;
}

/** 页面树节点 */
export interface DocPageTree {
  id: number;
  parentId: number;
  title: string;
  slug: string;
  pageType: DocPageType;
  status: DocPageStatus;
  icon?: string | null;
  sortNo: number;
  /** 是否启用页面级权限（M2-4） */
  restricted?: boolean;
  children: DocPageTree[];
}

/** 保存结果 */
export interface DocPageSaveResult {
  id: number;
  revision: number;
  status: DocPageStatus;
  savedAt?: string | null;
}

/** 版本快照 */
export interface DocVersion {
  id: number;
  pageId: number;
  revisionNo: number;
  title: string;
  versionKind: DocVersionKind;
  changeSummary?: string | null;
  editorId: number;
  /** 正文 JSON 字符串（仅详情返回） */
  contentJson?: string | null;
  createTime?: string | null;
}

/** 页面附件引用 */
export interface DocAttachment {
  id: number;
  fileId: number;
  originalName: string;
  contentType: string;
  fileSize: number;
  usageType: DocAttachmentUsage;
  contentUrl: string;
  createTime?: string | null;
}

/** 页面评论（replies 为一级回复） */
export interface DocComment {
  id: number;
  pageId: number;
  parentId: number;
  anchorBlockId?: string | null;
  anchorText?: string | null;
  content: string;
  authorId: number;
  resolved: boolean;
  resolvedBy?: number | null;
  resolvedAt?: string | null;
  createTime?: string | null;
  replies: DocComment[];
}

/** 回收站条目（M2） */
export interface DocRecycleItem {
  pageId: number;
  title: string;
  pageType: DocPageType;
  parentId: number;
  parentTitle?: string | null;
  deletedBy?: number | null;
  deletedAt?: string | null;
  /** 子树内除根节点外的页面数量 */
  childCount: number;
}

/** 搜索结果 */
export interface DocSearchItem {
  pageId: number;
  spaceId: number;
  spaceName: string;
  title: string;
  slug: string;
  status: DocPageStatus;
  revision: number;
  snippet: string;
  updateTime?: string | null;
}

/** 空间标签（M2-5） */
export interface DocTag {
  id: number;
  name: string;
  color?: string | null;
  /** 当前用户可读的页面数量（空间标签列表返回） */
  pageCount?: number | null;
}

/** 页面级权限角色（M2-4） */
export type DocAclRole = 'READ' | 'EDIT';

/** 页面级权限授权项 */
export interface DocAclItem {
  userId: number;
  aclRole: DocAclRole;
  createTime?: string | null;
}

/** 页面级权限配置 */
export interface DocPageAcl {
  pageId: number;
  restricted: boolean;
  items: DocAclItem[];
}

/** 反向链接（M2-5） */
export interface DocBacklink {
  pageId: number;
  title: string;
  spaceId: number;
  spaceName: string;
  slug: string;
  linkText?: string | null;
  updateTime?: string | null;
}

/** 页面动态（M2-5） */
export interface DocActivity {
  id: number;
  action: string;
  actorId?: number | null;
  detail?: string | null;
  createTime?: string | null;
}

/** 最近访问（M2-5） */
export interface DocRecent {
  pageId: number;
  title: string;
  spaceId: number;
  spaceName: string;
  slug: string;
  visitedAt?: string | null;
  updateTime?: string | null;
}

/** 评论未读状态（M2-7） */
export interface DocCommentUnread {
  unread: number;
  lastReadCommentId: number;
}

/** 收藏条目 */
export interface DocStarItem {
  pageId: number;
  spaceId: number;
  spaceName: string;
  title: string;
  slug: string;
  status: DocPageStatus;
  starredAt?: string | null;
  updateTime?: string | null;
}
