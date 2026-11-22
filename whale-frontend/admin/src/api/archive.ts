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
  return get<ArchiveCategoryVO[]>('/archive/v1/category/tree');
}

/** 新增门类 */
export function createCategory(data: ArchiveCategorySaveRequest): Promise<ArchiveCategoryVO> {
  return post<ArchiveCategoryVO>('/archive/v1/category', data);
}

/** 修改门类 */
export function updateCategory(
  id: string,
  data: ArchiveCategorySaveRequest,
): Promise<ArchiveCategoryVO> {
  return put<ArchiveCategoryVO>(`/archive/v1/category/${id}`, data);
}

/** 删除门类 */
export function deleteCategory(id: string): Promise<void> {
  return del<void>(`/archive/v1/category/${id}`);
}

/** 分页查询规则 */
export function pageRules(params: ArchiveRuleQueryParams): Promise<PageResult<ArchiveRuleVO>> {
  return get<PageResult<ArchiveRuleVO>>('/archive/v1/rule/page', { ...params });
}

/** 新增规则 */
export function createRule(data: ArchiveRuleSaveRequest): Promise<ArchiveRuleVO> {
  return post<ArchiveRuleVO>('/archive/v1/rule', data);
}

/** 修改规则 */
export function updateRule(id: string, data: ArchiveRuleSaveRequest): Promise<ArchiveRuleVO> {
  return put<ArchiveRuleVO>(`/archive/v1/rule/${id}`, data);
}

/** 删除规则 */
export function deleteRule(id: string): Promise<void> {
  return del<void>(`/archive/v1/rule/${id}`);
}

/** 分页查询条目 */
export function pageItems(params: ArchiveItemQueryParams): Promise<PageResult<ArchiveItemVO>> {
  return get<PageResult<ArchiveItemVO>>('/archive/v1/item/page', { ...params });
}

/** 条目详情 */
export function itemDetail(id: string): Promise<ArchiveItemDetailVO> {
  return get<ArchiveItemDetailVO>(`/archive/v1/item/${id}`);
}

/** 整理条目 */
export function editItem(id: string, data: ArchiveItemEditRequest): Promise<ArchiveItemVO> {
  return put<ArchiveItemVO>(`/archive/v1/item/${id}`, data);
}

/** 确认归档 */
export function confirmItem(id: string): Promise<ArchiveItemVO> {
  return post<ArchiveItemVO>(`/archive/v1/item/${id}/confirm`);
}

/** 销毁条目 */
export function destroyItem(id: string): Promise<void> {
  return post<void>(`/archive/v1/item/${id}/destroy`);
}

/** 档案统计 */
export function archiveStats(): Promise<ArchiveStatsVO> {
  return get<ArchiveStatsVO>('/archive/v1/stats');
}

/** 同步状态 */
export function syncStatus(): Promise<ArchiveSyncStatusVO> {
  return get<ArchiveSyncStatusVO>('/archive/v1/sync/status');
}

/** 手动触发同步 */
export function runSync(): Promise<ArchiveSyncResultVO> {
  return post<ArchiveSyncResultVO>('/archive/v1/sync/run');
}

/** 构造仅含 Authorization 的请求头（token 缺失时不加） */
function authHeaders(): HeadersInit {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

/** 可内联预览的 MIME 前缀（与后端 ArchiveFileResponses 保持一致） */
const INLINE_PREFIXES = ['image/', 'video/', 'audio/', 'text/'];

/** 可内联预览的完整 MIME */
const INLINE_TYPES = [
  'application/pdf',
  'application/json',
  'application/xml',
  'application/xhtml+xml',
];

/** 在线预览类型 */
export type ArchivePreviewKind = 'image' | 'video' | 'audio' | 'pdf' | 'text';

/** 解析档案文件的 MIME 基础类型（去掉 charset 等参数） */
function baseContentType(file: ArchiveFileVO): string {
  const raw = file.contentType?.trim().toLowerCase() ?? '';
  return raw.split(';')[0]?.trim() ?? '';
}

/**
 * 判断档案文件是否支持在线预览：图片 / 音视频 / 文本 / PDF 等允许内联渲染。
 *
 * @param file 档案文件引用
 * @returns 支持预览时返回 true
 */
export function isPreviewableFile(file: ArchiveFileVO): boolean {
  const base = baseContentType(file);
  if (!base) {
    return false;
  }
  return INLINE_TYPES.includes(base) || INLINE_PREFIXES.some((prefix) => base.startsWith(prefix));
}

/**
 * 解析预览渲染方式，不支持时返回 null。
 *
 * @param file 档案文件引用
 * @returns 预览类型
 */
export function previewKindOf(file: ArchiveFileVO): ArchivePreviewKind | null {
  const base = baseContentType(file);
  if (base.startsWith('image/')) {
    return 'image';
  }
  if (base.startsWith('video/')) {
    return 'video';
  }
  if (base.startsWith('audio/')) {
    return 'audio';
  }
  if (base === 'application/pdf') {
    return 'pdf';
  }
  if (base.startsWith('text/') || base === 'application/json' || base === 'application/xml') {
    return 'text';
  }
  return null;
}

/**
 * 取档案文件预览内容并生成对象 URL（调用方需在关闭预览时 revoke）。
 *
 * @param itemId 条目ID
 * @param file 档案文件引用
 * @returns 可直接用于 img / video / audio / iframe 的对象 URL
 */
export async function fetchItemPreviewUrl(itemId: string, file: ArchiveFileVO): Promise<string> {
  const response = await fetch(`/archive/v1/item/${itemId}/files/${file.id}/preview`, {
    method: 'GET',
    headers: authHeaders(),
  });
  if (!response.ok) {
    throw new Error(`预览失败（HTTP ${response.status}）`);
  }
  const blob = await response.blob();
  return URL.createObjectURL(blob);
}

/** 下载档案文件：取 blob 后用临时 <a download> 触发保存 */
export async function downloadItemFile(itemId: string, file: ArchiveFileVO): Promise<void> {
  const response = await fetch(`/archive/v1/item/${itemId}/files/${file.id}/download`, {
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
