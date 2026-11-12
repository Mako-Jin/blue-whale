// 论坛接口（M0 门面 meta + M1 版块/帖子/楼层）
//
// 路径一律相对（`/forum/xxx`）：网关不剥 `/forum` 一级前缀（REQ-ARCH-003），
// 前端路径必须与后端「类前缀 + 方法映射」逐字一致，写错只在点击时 404。
// `check-api-path-drift.py` 会扫这些字符串，改路径前先跑它。

import { request } from './client';

/** 非功能上限（与后端 ForumMetaVO.Limits 一一对应） */
export interface ForumLimits {
  maxTitleLength: number;
  maxContentLength: number;
  maxPostsPerThread: number;
  pageSizeDefault: number;
  pageSizeMax: number;
  maxPageNumber: number;
  maxMentionsPerPost: number;
  postIntervalSeconds: number;
  replyIntervalSeconds: number;
  hotRefreshMinutes: number;
}

/** 门面信息（枚举由服务端下发，前端不硬编码） */
export interface ForumMeta {
  service: string;
  version: string;
  threadTypes: string[];
  threadStatuses: string[];
  boardVisibilities: string[];
  auditModes: string[];
  reportStatuses: string[];
  reactionTypes: string[];
  moderationActions: string[];
  anonymousEnabled: boolean;
  limits: ForumLimits;
}

/** 分页信封（与后端 PageResult 字段一致） */
export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
}

/** 版块（扁平结构，按 parentId 组树） */
export interface BoardItem {
  id: string;
  parentId?: string | null;
  code: string;
  name: string;
  description?: string | null;
  icon?: string | null;
  visibility: string;
  auditMode: number;
  postPolicy: string;
  sortNo: number;
  status: number;
}

/** 帖子列表项 */
export interface ThreadListItem {
  id: string;
  boardId: string;
  threadNo: string;
  threadType: string;
  title: string;
  summary?: string | null;
  authorId: string;
  anonymous: number;
  anonymousNo?: string | null;
  status: string;
  pinned: number;
  essence: number;
  replyCount: number;
  viewCount: number;
  likeCount: number;
  lastReplyTime?: string | null;
  publishTime?: string | null;
}

/** 帖子详情 */
export interface ThreadDetail extends ThreadListItem {
  content?: string | null;
  tags?: string | null;
  nextFloor: number;
  editable: boolean;
  deletable: boolean;
  auditRemark?: string | null;
  createTime?: string | null;
}

/** 楼层 */
export interface PostItem {
  id: string;
  floor: number;
  authorId: string;
  anonymous: number;
  anonymousNo?: string | null;
  content: string;
  likeCount: number;
  replyToPostId?: string | null;
  createTime?: string | null;
}

/** 读取门面信息 */
export function fetchForumMeta(): Promise<ForumMeta> {
  return request<ForumMeta>('/forum/meta');
}

/** 可见版块列表 */
export function fetchBoards(): Promise<BoardItem[]> {
  return request<BoardItem[]>('/forum/board/page');
}

/** 帖子列表 */
export function fetchThreads(params: {
  boardId: string;
  sort?: string;
  keyword?: string;
  essenceOnly?: boolean;
  current?: number;
  size?: number;
}): Promise<PageResult<ThreadListItem>> {
  const query = new URLSearchParams();
  query.set('boardId', params.boardId);
  if (params.sort) {
    query.set('sort', params.sort);
  }
  if (params.keyword) {
    query.set('keyword', params.keyword);
  }
  if (params.essenceOnly) {
    query.set('essenceOnly', 'true');
  }
  query.set('current', String(params.current ?? 1));
  query.set('size', String(params.size ?? 20));
  return request<PageResult<ThreadListItem>>(`/forum/thread/page?${query.toString()}`);
}

/** 帖子详情 */
export function fetchThread(id: string): Promise<ThreadDetail> {
  return request<ThreadDetail>(`/forum/thread/${id}`);
}

/** 发帖 */
export function createThread(body: {
  boardId: string;
  threadType?: string;
  title: string;
  content: string;
  tags?: string;
}): Promise<ThreadDetail> {
  return request<ThreadDetail>('/forum/thread', { method: 'POST', body });
}

/** 楼层分页 */
export function fetchPosts(
  threadId: string,
  current = 1,
  size = 20,
): Promise<PageResult<PostItem>> {
  return request<PageResult<PostItem>>(
    `/forum/thread/${threadId}/post/page?current=${current}&size=${size}`,
  );
}

/** 回帖 */
export function createPost(
  threadId: string,
  body: { content: string; replyToPostId?: string },
): Promise<PostItem> {
  return request<PostItem>(`/forum/thread/${threadId}/post`, { method: 'POST', body });
}

// ---------------------------------------------------------------------------
// M2：治理（审核台 / 举报工单 / 词库 / 通知补偿）
// 路径与后端 ForumAdminController、ReportController 逐字对应
// ---------------------------------------------------------------------------

/** 待审条目（帖子与楼层共用） */
export interface AuditItem {
  targetType: 'THREAD' | 'POST';
  targetId: string;
  threadId: string;
  title: string;
  summary: string;
  authorId: string;
  boardId?: string | null;
  floor?: number | null;
  status: string;
  createTime?: string | null;
}

/** 举报工单 */
export interface ReportItem {
  id: string;
  targetType: string;
  targetId: string;
  targetTitle: string;
  reporterId: string;
  reportType: string;
  description?: string | null;
  status: string;
  handlerId?: string | null;
  handleRemark?: string | null;
  handleTime?: string | null;
  createTime?: string | null;
}

/** 敏感词词条 */
export interface SensitiveWordItem {
  id: string;
  word: string;
  action: string;
  category?: string | null;
  status: number;
  createTime?: string | null;
}

/** 待审队列 */
export function fetchAuditPage(
  targetType: 'THREAD' | 'POST',
  current = 1,
  size = 20,
): Promise<PageResult<AuditItem>> {
  return request<PageResult<AuditItem>>(
    `/forum/admin/audit/page?targetType=${targetType}&current=${current}&size=${size}`,
  );
}

/** 审核通过 */
export function passAudit(body: {
  targetType: string;
  targetId: string;
  remark?: string;
}): Promise<void> {
  return request<void>('/forum/admin/audit/pass', { method: 'POST', body });
}

/** 审核驳回（remark 必填，后端 30020） */
export function rejectAudit(body: {
  targetType: string;
  targetId: string;
  remark: string;
}): Promise<void> {
  return request<void>('/forum/admin/audit/reject', { method: 'POST', body });
}

/** 举报工单分页 */
export function fetchReports(
  status: string,
  current = 1,
  size = 20,
): Promise<PageResult<ReportItem>> {
  const suffix = status ? `&status=${status}` : '';
  return request<PageResult<ReportItem>>(
    `/forum/admin/report/page?current=${current}&size=${size}${suffix}`,
  );
}

/** 处理举报工单 */
export function handleReport(
  id: string,
  body: { status: string; remark?: string; action?: string; muteDays?: number },
): Promise<void> {
  return request<void>(`/forum/admin/report/${id}/handle`, { method: 'POST', body });
}

/** 提交举报（鸣鼓） */
export function submitReport(body: {
  targetType: string;
  targetId: string;
  reportType: string;
  description?: string;
}): Promise<string> {
  return request<string>('/forum/report', { method: 'POST', body });
}

/** 词库分页 */
export function fetchSensitiveWords(
  keyword: string,
  current = 1,
  size = 20,
): Promise<PageResult<SensitiveWordItem>> {
  const suffix = keyword ? `&keyword=${encodeURIComponent(keyword)}` : '';
  return request<PageResult<SensitiveWordItem>>(
    `/forum/admin/sensitive/page?current=${current}&size=${size}${suffix}`,
  );
}

/** 新增/更新词条 */
export function saveSensitiveWord(body: {
  word: string;
  action: string;
  category?: string;
}): Promise<string> {
  return request<string>('/forum/admin/sensitive', { method: 'POST', body });
}

/** 删除词条 */
export function deleteSensitiveWord(id: string): Promise<void> {
  return request<void>(`/forum/admin/sensitive/${id}`, { method: 'DELETE' });
}

/** 重载词库缓存 */
export function reloadSensitiveWords(): Promise<number> {
  return request<number>('/forum/admin/sensitive/reload', { method: 'POST' });
}

/** 补偿投递待发通知 */
export function flushNotify(): Promise<number> {
  return request<number>('/forum/admin/notify/flush', { method: 'POST' });
}
