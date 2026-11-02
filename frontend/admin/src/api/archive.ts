// 档案管理 API（经网关 → whale-archive /archive）
// 下载需要读取二进制 blob，统一 HTTP 客户端只解析 JSON 信封，故单独走原生 fetch。

import { del, get, post, put, type PageResult } from './client';
import { getToken } from './token';

/** 门类树节点 */
export interface ArchiveCategoryVO {
  id: string;
  code: string;
  name: string;
  parentId?: string;
  level?: number;
  retentionYears?: number;
  securityLevel?: number;
  sort?: number;
  status?: number;
  children?: ArchiveCategoryVO[];
}

/** 门类保存请求 */
export interface ArchiveCategorySaveRequest {
  code: string;
  name: string;
  parentId?: number | string;
  retentionYears?: number;
  securityLevel?: number;
  sort?: number;
  status?: number;
}

/** 归档规则 */
export interface ArchiveRuleVO {
  id: string;
  definitionCode: string;
  categoryId?: string;
  categoryName?: string;
  retentionYears?: number;
  securityLevel?: number;
  autoArchive?: number;
  enabled?: number;
  createTime?: string;
}

/** 规则保存请求 */
export interface ArchiveRuleSaveRequest {
  definitionCode: string;
  categoryId: string;
  retentionYears?: number;
  securityLevel?: number;
  autoArchive?: number;
  enabled?: number;
}

/** 规则分页查询条件 */
export interface ArchiveRuleQueryParams {
  definitionCode?: string;
  categoryId?: string;
  enabled?: number;
  current: number;
  size: number;
}

/** 档案条目 */
export interface ArchiveItemVO {
  id: string;
  archiveNo?: string;
  title?: string;
  categoryId?: string;
  categoryName?: string;
  categoryPath?: string;
  year?: number;
  retentionYears?: number;
  expireAt?: string;
  securityLevel?: number;
  sourceType?: string;
  sourceId?: string;
  definitionCode?: string;
  businessKey?: string;
  initiator?: string;
  finishedAt?: string;
  itemStatus?: number;
  summary?: string;
  keywords?: string;
  snapshotFileId?: string;
  snapshotHash?: string;
  archivedAt?: string;
  createTime?: string;
}

/** 档案条目查询条件 */
export interface ArchiveItemQueryParams {
  keyword?: string;
  categoryId?: string;
  year?: number;
  securityLevel?: number;
  itemStatus?: number;
  current: number;
  size: number;
}

/** 条目整理请求 */
export interface ArchiveItemEditRequest {
  categoryId: string;
  title?: string;
  securityLevel?: number;
  retentionYears?: number;
  summary?: string;
  keywords?: string;
}

/** 档案文件引用 */
export interface ArchiveFileVO {
  id: string;
  fileId?: string;
  fileRole?: string;
  originalName?: string;
  fileSize?: number;
  contentType?: string;
  md5?: string;
  sort?: number;
}

/** 条目详情 */
export interface ArchiveItemDetailVO {
  item: ArchiveItemVO;
  files: ArchiveFileVO[];
  sourceStatus?: number;
}

/** 档案统计 */
export interface ArchiveStatsVO {
  total: number;
  pending: number;
  archived: number;
  destroyed: number;
  byYear: Record<string, number>;
  bySecurity: Record<string, number>;
  byCategory: Record<string, number>;
}

/** 同步状态 */
export interface ArchiveSyncStatusVO {
  enabled: boolean;
  cron?: string;
  cursorTime?: string;
  lastRunAt?: string;
  lastResult?: string;
  archivedCount: number;
  pendingCount: number;
  failedCount: number;
}

/** 同步结果 */
export interface ArchiveSyncResultVO {
  pulled: number;
  archived: number;
  pending: number;
  skipped: number;
  failed: number;
  cursorTime?: string;
  startedAt?: string;
  finishedAt?: string;
}

/** 查询门类树 */
export function categoryTree(): Promise<ArchiveCategoryVO[]> {
  return get<ArchiveCategoryVO[]>('/archive/category/tree');
}

/** 新增门类 */
export function createCategory(data: ArchiveCategorySaveRequest): Promise<ArchiveCategoryVO> {
  return post<ArchiveCategoryVO>('/archive/category', data);
}

/** 修改门类 */
export function updateCategory(
  id: string,
  data: ArchiveCategorySaveRequest,
): Promise<ArchiveCategoryVO> {
  return put<ArchiveCategoryVO>(`/archive/category/${id}`, data);
}

/** 删除门类 */
export function deleteCategory(id: string): Promise<void> {
  return del<void>(`/archive/category/${id}`);
}

/** 分页查询规则 */
export function pageRules(params: ArchiveRuleQueryParams): Promise<PageResult<ArchiveRuleVO>> {
  return get<PageResult<ArchiveRuleVO>>('/archive/rule/page', { ...params });
}

/** 新增规则 */
export function createRule(data: ArchiveRuleSaveRequest): Promise<ArchiveRuleVO> {
  return post<ArchiveRuleVO>('/archive/rule', data);
}

/** 修改规则 */
export function updateRule(id: string, data: ArchiveRuleSaveRequest): Promise<ArchiveRuleVO> {
  return put<ArchiveRuleVO>(`/archive/rule/${id}`, data);
}

/** 删除规则 */
export function deleteRule(id: string): Promise<void> {
  return del<void>(`/archive/rule/${id}`);
}

/** 分页查询条目 */
export function pageItems(params: ArchiveItemQueryParams): Promise<PageResult<ArchiveItemVO>> {
  return get<PageResult<ArchiveItemVO>>('/archive/item/page', { ...params });
}

/** 条目详情 */
export function itemDetail(id: string): Promise<ArchiveItemDetailVO> {
  return get<ArchiveItemDetailVO>(`/archive/item/${id}`);
}

/** 整理条目 */
export function editItem(id: string, data: ArchiveItemEditRequest): Promise<ArchiveItemVO> {
  return put<ArchiveItemVO>(`/archive/item/${id}`, data);
}

/** 确认归档 */
export function confirmItem(id: string): Promise<ArchiveItemVO> {
  return post<ArchiveItemVO>(`/archive/item/${id}/confirm`);
}

/** 销毁条目 */
export function destroyItem(id: string): Promise<void> {
  return post<void>(`/archive/item/${id}/destroy`);
}

/** 档案统计 */
export function archiveStats(): Promise<ArchiveStatsVO> {
  return get<ArchiveStatsVO>('/archive/stats');
}

/** 同步状态 */
export function syncStatus(): Promise<ArchiveSyncStatusVO> {
  return get<ArchiveSyncStatusVO>('/archive/sync/status');
}

/** 手动触发同步 */
export function runSync(): Promise<ArchiveSyncResultVO> {
  return post<ArchiveSyncResultVO>('/archive/sync/run');
}

/** 构造仅含 Authorization 的请求头（token 缺失时不加） */
function authHeaders(): HeadersInit {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

/** 下载档案文件：取 blob 后用临时 <a download> 触发保存 */
export async function downloadItemFile(itemId: string, file: ArchiveFileVO): Promise<void> {
  const response = await fetch(`/archive/item/${itemId}/files/${file.id}/download`, {
    method: 'GET',
    headers: authHeaders(),
  });
  if (!response.ok) {
    throw new Error(`下载失败（HTTP ${response.status}）`);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = file.originalName ?? `archive-${itemId}`;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  URL.revokeObjectURL(url);
}
