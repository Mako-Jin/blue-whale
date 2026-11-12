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
