// PDM 图文档中心 API（经网关 /pdm/** 转发到 whale-pdm）
//
// 版本动作（上传/检入/发布/回滚）走独立动作端点；下载为二进制响应，
// 文件字节由 whale-file 保存，前端不感知内部文件 ID。

import { download, http, upload, type PageResult } from './client';

/** 文档类型 */
export type DocType = 'DRAWING' | 'DOC' | 'SPEC' | 'REPORT';

/** 文档状态 */
export type DocStatus = 'DRAFT' | 'RELEASED';

/** 版本状态 */
export type DocVersionStatus = 'DRAFT' | 'RELEASED' | 'OBSOLETE';

/** 版本来源 */
export type DocVersionSource = 'UPLOAD' | 'CHECKIN' | 'ROLLBACK';

/** 图文档目录（树形，children 由后端装配） */
export interface DocFolder {
  id: number;
  parentId: number;
  name: string;
  path: string;
  folderType: string;
  sortNo?: number;
  remark?: string;
  children?: DocFolder[];
}

/** 图文档主档 */
export interface PdmDocument {
  id: number;
  folderId: number;
  docNo: string;
  title: string;
  docType: DocType;
  partNo?: string;
  status: DocStatus;
  ownerId: number;
  ownerName?: string;
  description?: string;
  tags?: string;
  latestVersionId?: number;
  latestVersionNo?: string;
  currentVersionId?: number;
  checkedOutBy?: number;
  checkedOutTime?: string;
  createTime?: string;
  updateTime?: string;
}

/** 图文档版本 */
export interface PdmDocumentVersion {
  id: number;
  documentId: number;
  versionNo: string;
  status: DocVersionStatus;
  fileName: string;
  fileSize?: number;
  contentType?: string;
  checksum?: string;
  changeNote?: string;
  source: DocVersionSource;
  createdBy?: number;
  releasedBy?: number;
  releasedAt?: string;
  createTime?: string;
}

/** 文档分页查询条件 */
export interface DocumentQuery {
  current?: number;
  size?: number;
  folderId?: number;
  docType?: DocType;
  status?: DocStatus;
  partNo?: string;
  ownerId?: number;
  keyword?: string;
  beginDate?: string;
  endDate?: string;
}

/** 文档新增/编辑入参 */
export interface DocumentInput {
  folderId: number;
  docNo?: string;
  title: string;
  docType: DocType;
  partNo?: string;
  description?: string;
  tags?: string;
}

/** 目录新增/编辑入参 */
export interface FolderInput {
  parentId?: number;
  name: string;
  folderType?: string;
  sortNo?: number;
  remark?: string;
}

/** ACL 资源类型 */
export type AclResourceType = 'FOLDER' | 'DOCUMENT';

/** ACL 主体类型 */
export type AclSubjectType = 'USER' | 'ROLE' | 'ALL';

/** ACL 动作 */
export type AclAction = 'VIEW' | 'EDIT' | 'DOWNLOAD' | 'DELETE' | 'MANAGE';

/** ACL 效果 */
export type AclEffect = 'ALLOW' | 'DENY';

/** 图文档 ACL 规则 */
export interface PdmDocAcl {
  id: number;
  resourceType: AclResourceType;
  resourceId: number;
  subjectType: AclSubjectType;
  subjectId: string;
  action: AclAction;
  effect: AclEffect;
  createTime?: string;
  updateTime?: string;
}

/** ACL 新增入参（唯一键重复时后端幂等返回既有规则） */
export interface AclSavePayload {
  resourceType: AclResourceType;
  resourceId: number;
  subjectType: AclSubjectType;
  subjectId: string;
  action: AclAction;
  effect: AclEffect;
}

/** 查询目录树（首次访问时后端自动初始化默认目录） */
export function listFolders(): Promise<DocFolder[]> {
  return http.get<DocFolder[]>('/pdm/folder/tree');
}

/** 新建目录 */
export function createFolder(input: FolderInput): Promise<DocFolder> {
  return http.post<DocFolder>('/pdm/folder', input);
}

/** 更新目录（改名 / 移动 / 排序） */
export function updateFolder(id: number, input: FolderInput): Promise<DocFolder> {
  return http.put<DocFolder>(`/pdm/folder/${id}`, input);
}

/** 删除目录（要求空目录且无文档） */
export function deleteFolder(id: number): Promise<void> {
  return http.del<void>(`/pdm/folder/${id}`);
}

/** 分页查询文档 */
export function pageDocuments(query: DocumentQuery): Promise<PageResult<PdmDocument>> {
  return http.get<PageResult<PdmDocument>>('/pdm/document/page', { ...query });
}

/** 文档详情 */
export function getDocument(id: number): Promise<PdmDocument> {
  return http.get<PdmDocument>(`/pdm/document/${id}`);
}

/** 文档版本列表 */
export function listVersions(id: number): Promise<PdmDocumentVersion[]> {
  return http.get<PdmDocumentVersion[]>(`/pdm/document/${id}/versions`);
}

/** 新建文档 */
export function createDocument(input: DocumentInput): Promise<PdmDocument> {
  return http.post<PdmDocument>('/pdm/document', input);
}

/** 更新文档元数据 */
export function updateDocument(id: number, input: DocumentInput): Promise<PdmDocument> {
  return http.put<PdmDocument>(`/pdm/document/${id}`, input);
}

/** 删除文档 */
export function deleteDocument(id: number): Promise<void> {
  return http.del<void>(`/pdm/document/${id}`);
}

/** 上传新草稿版本 */
export function uploadVersion(
  id: number,
  file: File,
  changeNote?: string,
): Promise<PdmDocumentVersion> {
  const form = new FormData();
  form.append('file', file);
  const query = changeNote ? `?changeNote=${encodeURIComponent(changeNote)}` : '';
  return upload<PdmDocumentVersion>(`/pdm/document/${id}/versions${query}`, form);
}

/** 检出文档（加编辑锁） */
export function checkOut(id: number): Promise<PdmDocument> {
  return http.post<PdmDocument>(`/pdm/document/${id}/checkout`);
}

/** 检入文档（仅检出人本人可操作） */
export function checkIn(
  id: number,
  file: File,
  changeNote?: string,
): Promise<PdmDocumentVersion> {
  const form = new FormData();
  form.append('file', file);
  const query = changeNote ? `?changeNote=${encodeURIComponent(changeNote)}` : '';
  return upload<PdmDocumentVersion>(`/pdm/document/${id}/checkin${query}`, form);
}

/** 发布草稿版本 */
export function releaseVersion(id: number, versionId: number): Promise<PdmDocumentVersion> {
  return http.post<PdmDocumentVersion>(`/pdm/document/${id}/versions/${versionId}/release`);
}

/** 以历史版本为基线生成新草稿 */
export function rollbackVersion(id: number, versionId: number): Promise<PdmDocumentVersion> {
  return http.post<PdmDocumentVersion>(`/pdm/document/${id}/versions/${versionId}/rollback`);
}

/** 下载指定版本内容 */
export function downloadVersion(
  id: number,
  versionId: number,
): Promise<{ blob: Blob; filename: string }> {
  return download(`/pdm/document/${id}/versions/${versionId}/content`);
}

/** 查询目录或文档的 ACL 规则 */
export function listAcl(
  resourceType: AclResourceType,
  resourceId: number,
): Promise<PdmDocAcl[]> {
  return http.get<PdmDocAcl[]>('/pdm/acl', { resourceType, resourceId });
}

/** 新增 ACL 规则（唯一键重复时幂等返回既有规则） */
export function saveAcl(payload: AclSavePayload): Promise<PdmDocAcl> {
  return http.put<PdmDocAcl>('/pdm/acl', payload);
}

/** 删除 ACL 规则 */
export function deleteAcl(id: number): Promise<void> {
  return http.del<void>(`/pdm/acl/${id}`);
}
