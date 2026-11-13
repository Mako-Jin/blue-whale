// 视频运营 API（经网关 → whale-video /video）
// 权限码：sys:video:content:list / publish / offline / takedown / series:manage / browse

import { del, get, post, put, type PageResult } from './client';

/** 分页请求公共参数 */
export interface PageQuery {
  current?: number;
  size?: number;
}

/** 内容类型 */
export type ContentType = 'SHORT_VIDEO' | 'VIDEO' | 'MOVIE';

/** 发布状态 */
export type PublishState = 'DRAFT' | 'PENDING_REVIEW' | 'PUBLISHED' | 'OFFLINE';

/** 版权状态 */
export type CopyrightState = 'AUTHORIZED' | 'TAKEDOWN';

/** 可见性 */
export type ContentVisibility = 'PUBLIC' | 'PRIVATE';

/** 运营侧内容视图（含文件 ID，仅运营接口返回） */
export interface ContentView {
  id: string;
  contentType?: ContentType;
  title: string;
  subtitle?: string;
  description?: string;
  videoFileId?: string;
  coverFileId?: string;
  durationMs?: number;
  width?: number;
  height?: number;
  sizeBytes?: number;
  uploaderId?: string;
  creatorId?: string;
  categoryId?: string;
  seriesId?: string;
  episodeNo?: number;
  visibility?: ContentVisibility;
  publishState?: PublishState;
  copyrightState?: CopyrightState;
  source?: string;
  copyrightExpireAt?: string;
  takedownReason?: string;
  takedownAt?: string;
  playCount?: number;
  likeCount?: number;
  favoriteCount?: number;
  commentCount?: number;
  tagIds?: string[];
  publishedAt?: string;
  createTime?: string;
}

/** 内容分页查询参数 */
export interface ContentQuery extends PageQuery {
  keyword?: string;
  contentType?: ContentType;
  categoryId?: string;
  seriesId?: string;
  creatorId?: string;
  publishState?: PublishState;
  visibility?: ContentVisibility;
}

/** 系列视图（运营侧，封面通过 coverUrl 代理访问） */
export interface SeriesView {
  id: string;
  title: string;
  description?: string;
  contentType?: ContentType;
  categoryId?: string;
  creatorId?: string;
  creatorName?: string;
  publishState?: PublishState;
  episodeCount: number;
  sortNo?: number;
  coverUrl?: string;
  publishedAt?: string;
  createTime?: string;
}

/** 系列分页查询参数 */
export interface SeriesQuery extends PageQuery {
  keyword?: string;
  categoryId?: string;
  publishState?: PublishState;
}

/** 系列保存入参（coverFileId 为 null 保留原封面，0 清除） */
export interface SeriesSaveInput {
  title: string;
  description?: string;
  coverFileId?: string;
  categoryId?: string;
  creatorId?: string;
  sortNo?: number;
}

/** 分类（分类维护在后续里程碑开放，此处只读供筛选） */
export interface CategoryView {
  id: string;
  parentId: string;
  name: string;
  code: string;
  contentTypeScope: ContentType | 'ALL';
  sortNo: number;
}

/** 运营内容分页（含草稿与已下线） */
export function pageContents(params: ContentQuery): Promise<PageResult<ContentView>> {
  return get<PageResult<ContentView>>('/video/admin/content/page', { ...params });
}

/** 运营发布内容 */
export function publishContent(id: string): Promise<ContentView> {
  return post<ContentView>(`/video/admin/content/${id}/publish`);
}

/** 运营下架内容 */
export function offlineContent(id: string): Promise<ContentView> {
  return post<ContentView>(`/video/admin/content/${id}/offline`);
}

/** 版权下架内容（强制下线并阻断播放） */
export function takedownContent(id: string, reason: string): Promise<ContentView> {
  return post<ContentView>(`/video/admin/content/${id}/takedown`, { reason });
}

/** 运营系列分页（含草稿与已下架） */
export function pageSeries(params: SeriesQuery): Promise<PageResult<SeriesView>> {
  return get<PageResult<SeriesView>>('/video/admin/series/page', { ...params });
}

/** 新增系列草稿 */
export function createSeries(input: SeriesSaveInput): Promise<SeriesView> {
  return post<SeriesView>('/video/admin/series', input);
}

/** 编辑系列 */
export function updateSeries(id: string, input: SeriesSaveInput): Promise<SeriesView> {
  return put<SeriesView>(`/video/admin/series/${id}`, input);
}

/** 删除系列（存在已发布集数时后端拒绝） */
export function deleteSeries(id: string): Promise<void> {
  return del<void>(`/video/admin/series/${id}`);
}

/** 上架系列 */
export function publishSeries(id: string): Promise<SeriesView> {
  return post<SeriesView>(`/video/admin/series/${id}/publish`);
}

/** 下架系列 */
export function offlineSeries(id: string): Promise<SeriesView> {
  return post<SeriesView>(`/video/admin/series/${id}/offline`);
}

/** 启用分类列表（供内容 / 系列筛选与编辑） */
export function listVideoCategories(): Promise<CategoryView[]> {
  return get<CategoryView[]>('/video/categories');
}

/** 电影授权来源：自有版权 / 授权引进 / 公版 */
export type LicenseSource = 'OWNED' | 'LICENSED' | 'PUBLIC_DOMAIN';

/** 电影运营视图（含授权明细与海报 / 背景图文件 ID） */
export interface MovieAdminView extends ContentView {
  originalTitle?: string;
  director?: string;
  actors?: string;
  region?: string;
  language?: string;
  releaseDate?: string;
  rating?: number;
  licenseSource?: LicenseSource;
  licenseNo?: string;
  licenseExpireAt?: string;
  posterFileId?: string;
  backdropFileId?: string;
  posterUrl?: string;
  backdropUrl?: string;
}

/** 电影分页查询参数 */
export interface MovieQuery extends PageQuery {
  keyword?: string;
  categoryId?: string;
  creatorId?: string;
  publishState?: PublishState;
  sortBy?: 'LATEST' | 'HOT';
}

/** 电影入库 / 编辑入参（授权来源与到期时间必填） */
export interface MovieSaveInput {
  videoFileId?: string;
  title: string;
  subtitle?: string;
  description?: string;
  durationMs?: number;
  width?: number;
  height?: number;
  categoryId?: string;
  creatorId?: string;
  originalTitle?: string;
  director?: string;
  actors?: string;
  region?: string;
  language?: string;
  releaseDate?: string;
  rating?: number;
  licenseSource: LicenseSource;
  licenseNo?: string;
  licenseExpireAt: string;
  posterFileId?: string;
  backdropFileId?: string;
}

/** 用户侧电影视图（片单条目展示，不含文件 ID） */
export interface MoviePublicView {
  id: string;
  title: string;
  subtitle?: string;
  originalTitle?: string;
  director?: string;
  actors?: string;
  region?: string;
  language?: string;
  releaseDate?: string;
  rating?: number;
  durationMs?: number;
  playCount?: number;
  posterUrl?: string;
  backdropUrl?: string;
  playable?: boolean;
  publishedAt?: string;
}

/** 电影片单视图（运营侧，封面通过 coverUrl 代理访问） */
export interface MovieCollectionAdminView {
  id: string;
  title: string;
  description?: string;
  coverUrl?: string;
  publishState?: PublishState;
  itemCount: number;
  sortNo?: number;
  publishedAt?: string;
  createTime?: string;
}

/** 电影片单详情（含片单内电影） */
export interface MovieCollectionAdminDetailView extends MovieCollectionAdminView {
  items: MoviePublicView[];
}

/** 电影片单分页查询参数 */
export interface CollectionQuery extends PageQuery {
  keyword?: string;
  publishState?: PublishState;
}

/** 电影片单保存入参（coverFileId 为 null 保留原封面，0 清除） */
export interface CollectionSaveInput {
  title: string;
  description?: string;
  coverFileId?: string;
  sortNo?: number;
}

/** 运营统计概览 */
export interface StatOverview {
  totalContent: number;
  publishedContent: number;
  shortVideoCount: number;
  videoCount: number;
  movieCount: number;
  totalPlayCount: number;
  pendingCommentCount: number;
  expiringLicenseCount: number;
  collectionCount: number;
  creatorCount: number;
}

/** 评论审核状态：待审核 / 可见 / 已隐藏 */
export type CommentStatus = 'PENDING' | 'VISIBLE' | 'HIDDEN';

/** 评论审核视图（含审核状态） */
export interface CommentAuditView {
  id: string;
  contentId?: string;
  userId?: string;
  parentId?: string;
  content: string;
  likeCount?: number;
  createTime?: string;
  status?: CommentStatus;
}

/** 评论审核分页查询参数 */
export interface CommentAuditQuery extends PageQuery {
  status?: CommentStatus;
}

/** 创作者运营视图 */
export interface CreatorAdminView {
  id: string;
  userId?: string;
  displayName?: string;
  bio?: string;
  status?: number;
  active?: boolean;
  avatarUrl?: string;
  createTime?: string;
}

/** 创作者分页查询参数 */
export interface CreatorQuery extends PageQuery {
  keyword?: string;
  status?: number;
}

/** 创作者编辑入参（avatarFileId 为 null 保留原头像，0 清除） */
export interface CreatorUpdateInput {
  displayName?: string;
  bio?: string;
  status?: number;
  avatarFileId?: string;
}

/** 运营电影分页（含草稿与已下线） */
export function pageMovies(params: MovieQuery): Promise<PageResult<MovieAdminView>> {
  return get<PageResult<MovieAdminView>>('/video/admin/movies/page', { ...params });
}

/** 运营读取单部电影（编辑回填） */
export function getMovie(id: string): Promise<MovieAdminView> {
  return get<MovieAdminView>(`/video/admin/movies/${id}`);
}

/** 电影入库（草稿） */
export function createMovie(input: MovieSaveInput): Promise<MovieAdminView> {
  return post<MovieAdminView>('/video/admin/movies', input);
}

/** 编辑电影元数据与授权 */
export function updateMovie(id: string, input: MovieSaveInput): Promise<MovieAdminView> {
  return put<MovieAdminView>(`/video/admin/movies/${id}`, input);
}

/** 删除电影（内容逻辑删除 + 元数据物理删除） */
export function deleteMovie(id: string): Promise<void> {
  return del<void>(`/video/admin/movies/${id}`);
}

/** 发布电影（授权须仍在有效期） */
export function publishMovie(id: string): Promise<MovieAdminView> {
  return post<MovieAdminView>(`/video/admin/movies/${id}/publish`);
}

/** 下架电影 */
export function offlineMovie(id: string): Promise<MovieAdminView> {
  return post<MovieAdminView>(`/video/admin/movies/${id}/offline`);
}

/** 版权下架电影（强制下线并阻断播放） */
export function takedownMovie(id: string, reason: string): Promise<MovieAdminView> {
  return post<MovieAdminView>(`/video/admin/movies/${id}/takedown`, { reason });
}

/** 运营片单分页（含草稿与已下架） */
export function pageCollections(
  params: CollectionQuery,
): Promise<PageResult<MovieCollectionAdminView>> {
  return get<PageResult<MovieCollectionAdminView>>('/video/admin/collections/page', { ...params });
}

/** 运营读取单个片单（含全部条目） */
export function getCollection(id: string): Promise<MovieCollectionAdminDetailView> {
  return get<MovieCollectionAdminDetailView>(`/video/admin/collections/${id}`);
}

/** 新增片单草稿 */
export function createCollection(input: CollectionSaveInput): Promise<MovieCollectionAdminView> {
  return post<MovieCollectionAdminView>('/video/admin/collections', input);
}

/** 编辑片单 */
export function updateCollection(
  id: string,
  input: CollectionSaveInput,
): Promise<MovieCollectionAdminView> {
  return put<MovieCollectionAdminView>(`/video/admin/collections/${id}`, input);
}

/** 删除片单 */
export function deleteCollection(id: string): Promise<void> {
  return del<void>(`/video/admin/collections/${id}`);
}

/** 上架片单 */
export function publishCollection(id: string): Promise<MovieCollectionAdminView> {
  return post<MovieCollectionAdminView>(`/video/admin/collections/${id}/publish`);
}

/** 下架片单（不影响片单内电影状态） */
export function offlineCollection(id: string): Promise<MovieCollectionAdminView> {
  return post<MovieCollectionAdminView>(`/video/admin/collections/${id}/offline`);
}

/** 片单加片（重复加片幂等，单片单最多 200 部） */
export function addCollectionItem(
  id: string,
  contentId: string,
  sortNo?: number,
): Promise<MovieCollectionAdminDetailView> {
  return post<MovieCollectionAdminDetailView>(`/video/admin/collections/${id}/item`, {
    contentId,
    sortNo,
  });
}

/** 移除片单内电影（不存在时幂等） */
export function removeCollectionItem(
  id: string,
  contentId: string,
): Promise<MovieCollectionAdminDetailView> {
  return del<MovieCollectionAdminDetailView>(`/video/admin/collections/${id}/item/${contentId}`);
}

/** 运营统计概览（expiringDays 为授权到期提醒窗口，默认 7 天） */
export function fetchStatOverview(expiringDays?: number): Promise<StatOverview> {
  return get<StatOverview>('/video/admin/stat/overview', { expiringDays });
}

/** 评论审核分页 */
export function pageAuditComments(
  params: CommentAuditQuery,
): Promise<PageResult<CommentAuditView>> {
  return get<PageResult<CommentAuditView>>('/video/admin/comment/page', { ...params });
}

/** 审核通过（PENDING / HIDDEN → VISIBLE，幂等） */
export function approveComment(id: string): Promise<CommentAuditView> {
  return post<CommentAuditView>(`/video/admin/comment/${id}/approve`);
}

/** 隐藏评论（→ HIDDEN，幂等，立即对用户不可见） */
export function hideComment(id: string): Promise<CommentAuditView> {
  return post<CommentAuditView>(`/video/admin/comment/${id}/hide`);
}

/** 创作者分页 */
export function pageCreators(params: CreatorQuery): Promise<PageResult<CreatorAdminView>> {
  return get<PageResult<CreatorAdminView>>('/video/admin/creator/page', { ...params });
}

/** 编辑创作者资料与状态 */
export function updateCreator(id: string, input: CreatorUpdateInput): Promise<CreatorAdminView> {
  return put<CreatorAdminView>(`/video/admin/creator/${id}`, input);
}
