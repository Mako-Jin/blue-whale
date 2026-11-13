// OA 协同 - 网盘 API 层：文件夹、文件、授权与配额
//
// 文件上传复用平台 /file/upload（见 api/file.ts），网盘仅登记文件引用。

import { downloadBlob, http } from './client';
import type { PageResult } from './client';

/** 网盘可见范围 */
export type OaDriveScope = 'PERSONAL' | 'DEPARTMENT';

/** 授权主体类型 */
export type OaDriveSubjectType = 'USER' | 'DEPT';

/** 授权效果 */
export type OaDriveAclEffect = 'ALLOW' | 'DENY';

/** 授权权限 */
export type OaDriveAclPermission = 'READ' | 'WRITE' | 'MANAGE';

/** 网盘文件夹 */
export interface OaDriveFolder {
  id: number;
  parentId: number;
  name: string;
  scope: OaDriveScope;
  deptId?: number;
  ownerId: number;
  ownerName?: string;
  sortNo?: number;
  createTime?: string;
  writable: boolean;
  manageable: boolean;
}

/** 网盘文件（引用平台上传记录） */
export interface OaDriveFile {
  id: number;
  folderId: number;
  fileId: number;
  fileName: string;
  fileSize: number;
  contentType?: string;
  uploaderId?: number;
  uploaderName?: string;
  createTime?: string;
}

/** 网盘授权条目 */
export interface OaDriveAcl {
  subjectType: OaDriveSubjectType;
  subjectId: number;
  subjectName?: string;
  effect: OaDriveAclEffect;
  permission: OaDriveAclPermission;
}

/** 网盘配额 */
export interface OaDriveQuota {
  usedBytes: number;
  totalBytes: number;
}

/** 新建文件夹入参 */
export interface OaDriveFolderInput {
  name: string;
  parentId: number;
  scope: OaDriveScope;
  deptId?: number;
  sortNo?: number;
}

/** 更新文件夹入参 */
export interface OaDriveFolderUpdate {
  name: string;
  sortNo?: number;
}

/** 文件查询 */
export interface OaDriveFileQuery {
  folderId?: number;
  keyword?: string;
  current?: number;
  size?: number;
}

/** 文件登记入参 */
export interface OaDriveFileInput {
  folderId: number;
  fileId: number;
}

/** 拼装查询串（跳过空值） */
function buildQuery(params: object): string {
  const search = new URLSearchParams();
  Object.entries(params as Record<string, unknown>).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value));
    }
  });
  const query = search.toString();
  return query ? `?${query}` : '';
}

/** 查询指定父目录下的文件夹 */
export function listDriveFolders(parentId: number): Promise<OaDriveFolder[]> {
  return http.get<OaDriveFolder[]>(`/oa/drive/folders${buildQuery({ parentId })}`);
}

/** 新建文件夹 */
export function createDriveFolder(input: OaDriveFolderInput): Promise<OaDriveFolder> {
  return http.post<OaDriveFolder>('/oa/drive/folders', input);
}

/** 更新文件夹（重命名/排序） */
export function updateDriveFolder(id: number, input: OaDriveFolderUpdate): Promise<OaDriveFolder> {
  return http.put<OaDriveFolder>(`/oa/drive/folders/${id}`, input);
}

/** 删除文件夹 */
export function deleteDriveFolder(id: number): Promise<void> {
  return http.del<void>(`/oa/drive/folders/${id}`);
}

/** 分页查询文件 */
export function pageDriveFiles(params: OaDriveFileQuery = {}): Promise<PageResult<OaDriveFile>> {
  return http.get<PageResult<OaDriveFile>>(`/oa/drive/files${buildQuery(params)}`);
}

/** 登记已上传文件到文件夹 */
export function registerDriveFile(input: OaDriveFileInput): Promise<OaDriveFile> {
  return http.post<OaDriveFile>('/oa/drive/files', input);
}

/** 删除网盘文件 */
export function deleteDriveFile(id: number): Promise<void> {
  return http.del<void>(`/oa/drive/files/${id}`);
}

/** 查询文件夹授权 */
export function listDriveAcls(folderId: number): Promise<OaDriveAcl[]> {
  return http.get<OaDriveAcl[]>(`/oa/drive/folders/${folderId}/acls`);
}

/** 替换文件夹授权 */
export function replaceDriveAcls(folderId: number, entries: OaDriveAcl[]): Promise<OaDriveAcl[]> {
  return http.put<OaDriveAcl[]>(`/oa/drive/folders/${folderId}/acls`, { entries });
}

/** 查询当前用户网盘配额 */
export function getDriveQuota(): Promise<OaDriveQuota> {
  return http.get<OaDriveQuota>('/oa/drive/quota');
}

/** 下载网盘文件（经 OA 代理流式下载，Authorization 与 401 续期由 http 客户端处理） */
export async function downloadDriveFile(id: number, fileName: string): Promise<void> {
  const result = await downloadBlob(`/oa/drive/files/${id}/download`);
  const url = URL.createObjectURL(result.blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = result.filename || fileName;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
