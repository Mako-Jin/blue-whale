// 论坛接口（M0 门面 meta + M1 版块/帖子/楼层）
//
// 路径一律相对（`/forum/xxx`）：网关不剥 `/forum` 一级前缀（REQ-ARCH-003），
// 前端路径必须与后端「类前缀 + 方法映射」逐字一致，写错只在点击时 404。
// `check-api-path-drift.py` 会扫这些字符串，改路径前先跑它。

import { getToken, request } from './client';

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
  collectCount: number;
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
  /** 当前用户是否已投花 */
  liked?: boolean;
  /** 当前用户是否已藏帖 */
  collected?: boolean;
  createTime?: string | null;
}

/** 互动状态（投花/藏帖按钮选中态 + 最新计数） */
export interface ReactionState {
  liked: boolean;
  collected: boolean;
  likeCount: number;
  collectCount: number;
}

/** 热榜条目（只读快照） */
export interface HotItem {
  rankNo: number;
  threadId: string;
  boardId?: string | null;
  title: string;
  summary?: string | null;
  authorId?: string | null;
  replyCount: number;
  likeCount: number;
  viewCount: number;
  score: string | number;
  snapshotTime?: string | null;
}

/** 被引用楼层摘要（未引用时为 null；被引楼层已删时 deleted=true，正文不返回） */
export interface QuotedPost {
  postId: string;
  floor?: number | null;
  authorId?: string | null;
  excerpt?: string | null;
  deleted?: boolean;
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
  /** 被引用楼层摘要：服务端批量回填，软删后只剩 deleted 标记 */
  quoted?: QuotedPost | null;
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

/** 帖子列表（boardId 为空表示全站：精华榜/标签页跨版块） */
export function fetchThreads(params: {
  boardId?: string;
  sort?: string;
  keyword?: string;
  essenceOnly?: boolean;
  authorId?: string;
  /** 起始日期（含），yyyy-MM-dd */
  from?: string;
  /** 截止日期（含），yyyy-MM-dd */
  to?: string;
  /** 标签（精确匹配） */
  tag?: string;
  current?: number;
  size?: number;
}): Promise<PageResult<ThreadListItem>> {
  const query = new URLSearchParams();
  if (params.boardId) {
    query.set('boardId', params.boardId);
  }
  if (params.sort) {
    query.set('sort', params.sort);
  }
  if (params.keyword) {
    query.set('keyword', params.keyword);
  }
  if (params.essenceOnly) {
    query.set('essenceOnly', 'true');
  }
  if (params.authorId) {
    query.set('authorId', params.authorId);
  }
  if (params.from) {
    query.set('from', params.from);
  }
  if (params.to) {
    query.set('to', params.to);
  }
  if (params.tag) {
    query.set('tag', params.tag);
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

/**
 * 楼层分页
 *
 * order 必须交给服务端：前端只反转当前页数组，翻页时楼层会「跳」（页尾与下一页不连续）。
 */
export function fetchPosts(
  threadId: string,
  current = 1,
  size = 20,
  order: 'asc' | 'desc' = 'asc',
): Promise<PageResult<PostItem>> {
  return request<PageResult<PostItem>>(
    `/forum/thread/${threadId}/post/page?current=${current}&size=${size}&order=${order}`,
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

// ---------------------------------------------------------------------------
// M3：互动（投花/藏帖）、我的收藏、热榜
// 投花用 PUT、取消用 DELETE（同一路径），语义是「把状态置为目标值」，重复调用不报错
// ---------------------------------------------------------------------------

/** 投花 / 收藏（type=LIKE 投花，COLLECT 藏帖） */
export function addReaction(threadId: string, type: 'LIKE' | 'COLLECT'): Promise<ReactionState> {
  return request<ReactionState>(`/forum/reaction/thread/${threadId}?type=${type}`, {
    method: 'PUT',
  });
}

/** 取消投花 / 取消收藏（DELETE 同路径，语义是置为未投） */
export function cancelReaction(threadId: string, type: 'LIKE' | 'COLLECT'): Promise<ReactionState> {
  return request<ReactionState>(`/forum/reaction/thread/${threadId}?type=${type}`, {
    method: 'DELETE',
  });
}

/** 当前用户对某帖的互动状态 */
export function fetchMyReaction(threadId: string): Promise<ReactionState> {
  return request<ReactionState>(`/forum/thread/${threadId}/reactions/my`);
}

/** 我的收藏 */
export function fetchMyCollections(current = 1, size = 20): Promise<PageResult<ThreadListItem>> {
  return request<PageResult<ThreadListItem>>(
    `/forum/my/collections?current=${current}&size=${size}`,
  );
}

/** 热榜（读快照；window=24h/7d，非法值后端按 7d） */
export function fetchHot(window: '24h' | '7d' = '7d', limit = 20): Promise<HotItem[]> {
  return request<HotItem[]>(`/forum/hot?window=${window}&limit=${limit}`);
}

/** 手工刷新热榜快照（需 sys:forum:hot:refresh） */
export function refreshHot(): Promise<number> {
  return request<number>('/forum/hot/refresh', { method: 'POST' });
}

// ---------------------------------------------------------------------------
// M7：个人中心（F-07-01 个人主页 / F-07-02 我的内容 / F-07-03 鲸阶 / F-07-07 禁言）
// `/forum/my/*` 一律以身份头用户为准，不接受 userId 入参（防越权读）
// ---------------------------------------------------------------------------

/** 个人主页（只含论坛服务可得字段：头像/部门待 auth 通讯录，勋章属 P2） */
export interface Profile {
  userId: string;
  level: number;
  levelName: string;
  nextLevelExp?: string | number | null;
  expTotal: string | number;
  threadCount: string | number;
  likeCount: string | number;
  essenceCount: string | number;
  banned: boolean;
  banType?: string | null;
  banUntil?: string | null;
}

/** 我的回复条目（摘要由服务端截断，正文不返回） */
export interface MyPost {
  postId: string;
  threadId: string;
  threadTitle: string;
  floor?: number | null;
  summary?: string | null;
  likeCount: number;
  status: string;
  createTime?: string | null;
}

/** 个人主页：不传 userId 走 `/me`，传了走公开统计 */
export function fetchProfile(userId?: string): Promise<Profile> {
  return request<Profile>(userId ? `/forum/profile/${userId}` : '/forum/profile/me');
}

/** 我的帖子（含审核中/已处置，服务端不过滤状态） */
export function fetchMyThreads(current = 1, size = 20): Promise<PageResult<ThreadListItem>> {
  return request<PageResult<ThreadListItem>>(`/forum/my/threads?current=${current}&size=${size}`);
}

/** 我的回复（已删帖的楼层不出现） */
export function fetchMyPosts(current = 1, size = 20): Promise<PageResult<MyPost>> {
  return request<PageResult<MyPost>>(`/forum/my/posts?current=${current}&size=${size}`);
}

// ---------------------------------------------------------------------------
// M7-3 结契（关注作者 / 版块，F-05-03）
// 关注用 PUT、取关用 DELETE（同一路径），语义是「把状态置为目标值」
// ---------------------------------------------------------------------------

/** 关注对象类型 */
export type FollowTargetType = 'USER' | 'BOARD';

/** 关注态 */
export interface FollowState {
  following: boolean;
}

/** 我的关注条目（版块名前端本地映射） */
export interface FollowItem {
  targetType: FollowTargetType | string;
  targetId: string;
  createTime?: string | null;
}

/** 当前用户对某对象的关注态 */
export function fetchFollowState(
  targetType: FollowTargetType,
  targetId: string,
): Promise<FollowState> {
  return request<FollowState>(`/forum/follow/${targetType}/${targetId}`);
}

/** 关注作者 / 版块 */
export function addFollow(targetType: FollowTargetType, targetId: string): Promise<FollowState> {
  return request<FollowState>(`/forum/follow/${targetType}/${targetId}`, { method: 'PUT' });
}

/** 取关 */
export function removeFollow(targetType: FollowTargetType, targetId: string): Promise<FollowState> {
  return request<FollowState>(`/forum/follow/${targetType}/${targetId}`, { method: 'DELETE' });
}

/** 我的关注 */
export function fetchMyFollows(
  targetType?: FollowTargetType,
  current = 1,
  size = 20,
): Promise<PageResult<FollowItem>> {
  const query = new URLSearchParams({ current: String(current), size: String(size) });
  if (targetType) {
    query.set('targetType', targetType);
  }
  return request<PageResult<FollowItem>>(`/forum/my/follows?${query.toString()}`);
}

/** 结契动态（关注作者 ∪ 关注版块的已发布帖，排除本人；boardId 可选） */
export function fetchMyFeed(
  boardId?: string,
  current = 1,
  size = 20,
): Promise<PageResult<ThreadListItem>> {
  const query = new URLSearchParams({ current: String(current), size: String(size) });
  if (boardId) {
    query.set('boardId', boardId);
  }
  return request<PageResult<ThreadListItem>>(`/forum/my/feed?${query.toString()}`);
}

/** 编辑帖子（审核版块会退回待审；tags 传空串表示清空，不传表示不改） */
export function updateThread(
  threadId: string,
  body: { title: string; content: string; tags?: string; reason?: string },
): Promise<ThreadDetail> {
  return request<ThreadDetail>(`/forum/thread/${threadId}`, { method: 'PUT', body });
}

// ---------------------------------------------------------------------------
// M7-5 申诉（F-09-08）
// 只受理已处置对象：帖子 / 楼层限作者本人，禁言限本人且当前生效
// ---------------------------------------------------------------------------

/** 申诉对象类型 */
export type AppealTargetType = 'THREAD' | 'POST' | 'BAN';

/** 申诉工单 */
export interface AppealMaterial {
  fileId: string;
  fileName?: string | null;
  fileSize?: number | null;
  contentType?: string | null;
  downloadUrl?: string | null;
}

export interface AppealItem {
  id: string;
  userId: string;
  targetType: AppealTargetType | string;
  targetId: string;
  reason: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | string;
  handleRemark?: string | null;
  handleTime?: string | null;
  createTime?: string | null;
  materials?: AppealMaterial[];
}

/** 平台文件上传结果（/file/upload），ID 统一按字符串处理 */
export interface UploadedFile {
  id: string;
  fileName?: string;
  originalName?: string;
  fileSize?: number;
  contentType?: string;
}

/** 上传申诉材料：走平台 /file/upload，返回文件ID供提交工单引用 */
export function uploadAppealMaterial(file: File): Promise<UploadedFile> {
  const formData = new FormData();
  formData.append('file', file);
  return request<UploadedFile>('/file/v1/upload', { method: 'POST', body: formData }).then(
    (result) => {
      const id = result?.id === undefined || result?.id === null ? '' : String(result.id);
      if (!id) {
        throw new Error('上传成功但未返回文件标识');
      }
      return { ...result, id };
    },
  );
}

/** 提交申诉 */
export function submitAppeal(body: {
  targetType: AppealTargetType;
  targetId: string;
  reason: string;
  materialFileIds?: string[];
}): Promise<AppealItem> {
  return request<AppealItem>('/forum/appeal', { method: 'POST', body });
}

/** 我的申诉 */
export function fetchMyAppeals(current = 1, size = 20): Promise<PageResult<AppealItem>> {
  return request<PageResult<AppealItem>>(`/forum/my/appeals?current=${current}&size=${size}`);
}

/** 管理端申诉工单列表 */
export function fetchAdminAppeals(
  status?: string,
  current = 1,
  size = 10,
): Promise<PageResult<AppealItem>> {
  const query = new URLSearchParams({ current: String(current), size: String(size) });
  if (status) {
    query.set('status', status);
  }
  return request<PageResult<AppealItem>>(`/forum/admin/appeal/page?${query.toString()}`);
}

/** 复核申诉：approved=true 通过（禁言申诉会同时解禁），false 驳回 */
export function handleAppeal(
  appealId: string,
  approved: boolean,
  remark?: string,
): Promise<AppealItem> {
  return request<AppealItem>(`/forum/admin/appeal/${appealId}/handle`, {
    method: 'POST',
    body: { approved, remark },
  });
}

/**
 * 申诉材料图片预览：带鉴权取回二进制，返回可直接放进 img/src 的对象 URL。
 *
 * 不能走 `request()`：它只解 `Result` 信封，而这里是图片字节流。
 * 返回的对象 URL 由调用方在卸载时 `URL.revokeObjectURL` 释放。
 */
export async function fetchAppealMaterialPreview(fileId: string): Promise<string> {
  const token = getToken();
  const response = await fetch(`/forum/appeal/material/${fileId}/preview`, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  });
  if (!response.ok) {
    throw new Error(`材料预览加载失败（${response.status}）`);
  }
  return URL.createObjectURL(await response.blob());
}

/** 论坛经验流水条目 */
export interface ExpItem {
  event: string;
  amount: number;
  refThreadId: string;
  createTime: string;
}

/** 论坛经验汇总（总额 + 最近流水） */
export interface ExpSummary {
  total: number;
  recent: ExpItem[];
}

/** 我的论坛经验（只读，入账由发帖/投花/题金榜/金榜事件触发） */
export function fetchMyExp(): Promise<ExpSummary> {
  return request<ExpSummary>('/forum/exp/me');
}

// ---------------------------------------------------------------------------
// M4：检索（ngram 全文 + 单字 LIKE 回退）与运维（索引重建 / 计数对账）
// 计数类字段后端按 Long 序列化成字符串，前端统一 Number() 后再比较
// ---------------------------------------------------------------------------

/** 检索状态 */
export interface SearchStatus {
  engine: string;
  indexName: string;
  ngramTokenSize: number;
  minKeywordLength: number;
  likeFallback: boolean;
  totalThreads: string | number;
  indexedThreads: string | number;
  pendingBackfill: string | number;
}

/** 单条计数差异 */
export interface ReconcileItem {
  threadId: string;
  metric: 'REPLY' | 'LIKE' | 'COLLECT' | string;
  before: number;
  actual: number;
  fixed: boolean;
}

/** 计数对账结果 */
export interface ReconcileReport {
  batchNo: string;
  batchSize: number;
  diffCount: number;
  fixedCount: number;
  dryRun: boolean;
  items: ReconcileItem[];
  truncated: boolean;
}

/** 关键词检索（走 ngram 全文索引，关键词过短时后端自动回退 LIKE） */
export function searchThreads(params: {
  keyword: string;
  boardId?: string;
  essenceOnly?: boolean;
  authorId?: string;
  from?: string;
  to?: string;
  tag?: string;
  current?: number;
  size?: number;
}): Promise<PageResult<ThreadListItem>> {
  const query = new URLSearchParams();
  query.set('keyword', params.keyword);
  if (params.boardId) {
    query.set('boardId', params.boardId);
  }
  if (params.essenceOnly) {
    query.set('essenceOnly', 'true');
  }
  if (params.authorId) {
    query.set('authorId', params.authorId);
  }
  if (params.from) {
    query.set('from', params.from);
  }
  if (params.to) {
    query.set('to', params.to);
  }
  if (params.tag) {
    query.set('tag', params.tag);
  }
  query.set('current', String(params.current ?? 1));
  query.set('size', String(params.size ?? 20));
  return request<PageResult<ThreadListItem>>(`/forum/search?${query.toString()}`);
}

/** 标签聚合条目（F-06-07 标签页） */
export interface TagStat {
  tag: string;
  threadCount: string | number;
}

/** 标签聚合（按帖子数降序，只统计当前用户可见版块） */
export function fetchTags(limit = 50): Promise<TagStat[]> {
  return request<TagStat[]>(`/forum/tag/list?limit=${limit}`);
}

/** 检索状态（索引覆盖到哪、分词长度） */
export function fetchSearchStatus(): Promise<SearchStatus> {
  return request<SearchStatus>('/forum/search/status');
}

/** 重建检索索引（回填纯文本，需 sys:forum:manage:ops） */
export function reindexSearch(limit?: number): Promise<number> {
  const suffix = limit ? `?limit=${limit}` : '';
  return request<number>(`/forum/search/reindex${suffix}`, { method: 'POST' });
}

/** 计数对账（fix=false 只报告，fix=true 按真实值修正） */
export function reconcileCounts(fix = false, limit?: number): Promise<ReconcileReport> {
  const query = new URLSearchParams();
  query.set('fix', String(fix));
  if (limit) {
    query.set('limit', String(limit));
  }
  return request<ReconcileReport>(`/forum/admin/count/reconcile?${query.toString()}`, {
    method: 'POST',
  });
}
