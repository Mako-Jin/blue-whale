// Wave 5.77 视频平台前端 API（M2 对齐后端实际契约）
//
// 约定：
// 1. 所有路径均为相对路径，由基座 / Nginx 经网关 8080 代理（网关不剥离 /video 前缀）。
// 2. 公开视图不包含任何 fileId；封面与播放只使用代理 URL / 播放票据。
// 3. 流式播放：先 POST /video/play/ticket 取票据，再 GET /video/stream/{contentId}?ticket=...
//    票据默认 300s，建议在 refreshIn 秒后主动刷新（见 PlayTicket.refreshIn）。
// 4. 分页统一为 { records, total, current, size }；feed 使用字符串游标 { records, nextCursor, hasMore }。

import { http } from './client';

/** 内容类型 */
export type ContentType = 'SHORT_VIDEO' | 'VIDEO' | 'MOVIE';

/** 发布状态：草稿 / 待审核 / 已发布 / 已下架 */
export type PublishState = 'DRAFT' | 'PENDING_REVIEW' | 'PUBLISHED' | 'OFFLINE';

/** 版权状态：有效 / 版权下架（优先级最高，禁止签发播放票据） */
export type CopyrightState = 'AUTHORIZED' | 'TAKEDOWN';

/** 可见性：公开（租户内可见）/ 私密（仅上传者） */
export type ContentVisibility = 'PUBLIC' | 'PRIVATE';

/** 内容来源：原创 / 已授权 / 无版权争议素材 */
export type ContentSource = 'ORIGINAL' | 'AUTHORIZED' | 'ROYALTY_FREE';

/** 排序方式：最新 / 热门 */
export type FeedSort = 'LATEST' | 'HOT';

/** 内容卡片（feed / 列表 / 搜索结果通用；不含 fileId） */
export interface PublicContentView {
  id: number;
  contentType: ContentType;
  title: string;
  subtitle?: string;
  description?: string;
  durationMs?: number;
  width?: number;
  height?: number;
  format?: string;
  categoryId?: number;
  creatorId?: number;
  creatorName?: string;
  seriesId?: number;
  episodeNo?: number;
  visibility: ContentVisibility;
  playCount?: number;
  likeCount?: number;
  favoriteCount?: number;
  commentCount?: number;
  tags?: string[];
  coverUrl?: string;
  playable: boolean;
  publishedAt?: string;
}

/** 上传者侧内容视图（「我的作品」专用，含发布 / 版权状态，不含 fileId） */
export interface OwnerContentView extends PublicContentView {
  publishState?: PublishState;
  copyrightState?: CopyrightState;
  takedownReason?: string;
  createTime?: string;
}

/** 内容详情（GET /video/content/{id} 与各类型详情共用） */
export interface ContentDetailView extends PublicContentView {
  commentable: boolean;
  liked: boolean;
  favorited: boolean;
  progressMs?: number;
  ticketUrl?: string;
}

/** 本人作品编辑视图（含内部文件引用，仅上传者本人可读） */
export interface ContentEditView {
  id: number;
  contentType: ContentType;
  title?: string;
  subtitle?: string;
  description?: string;
  videoFileId?: number;
  coverFileId?: number;
  durationMs?: number;
  width?: number;
  height?: number;
  sizeBytes?: number;
  uploaderId?: number;
  creatorId?: number;
  categoryId?: number;
  seriesId?: number;
  episodeNo?: number;
  visibility?: ContentVisibility;
  publishState?: PublishState;
  copyrightState?: CopyrightState;
  source?: ContentSource;
  copyrightExpireAt?: string;
  takedownReason?: string;
  takedownAt?: string;
  playCount?: number;
  likeCount?: number;
  favoriteCount?: number;
  commentCount?: number;
  tagIds?: number[];
  publishedAt?: string;
  createTime?: string;
}

/** 短平快 feed 响应（键集 / 游标分页） */
export interface FeedView {
  records: PublicContentView[];
  nextCursor?: string;
  hasMore: boolean;
}

/** 分类 */
export interface CategoryView {
  id: number;
  parentId: number;
  name: string;
  code: string;
  contentTypeScope: ContentType | 'ALL';
  sortNo: number;
}

/** 标签 */
export interface TagView {
  id: number;
  name: string;
  tagType: 'TOPIC' | 'GENRE' | 'LANGUAGE';
  sortNo?: number;
}

/** 视频系列（公开字段，不暴露文件 ID；封面走 /video/series/{id}/cover） */
export interface SeriesView {
  id: number;
  title: string;
  description?: string;
  contentType: ContentType;
  categoryId?: number;
  creatorId?: number;
  creatorName?: string;
  publishState?: PublishState;
  episodeCount: number;
  sortNo?: number;
  coverUrl?: string;
  publishedAt?: string;
  createTime?: string;
}

/** 系列详情：基础信息 + 已发布的公开集数（按集数升序） */
export interface SeriesDetailView extends SeriesView {
  episodes: PublicContentView[];
}

/** 电影卡片（GET /video/movies/page，不含 fileId 与授权细节） */
export interface MovieView extends PublicContentView {
  originalTitle?: string;
  director?: string;
  actors?: string;
  region?: string;
  language?: string;
  releaseDate?: string;
  rating?: number;
  posterUrl?: string;
  backdropUrl?: string;
}

/** 电影详情（GET /video/movies/{id}，含收录本片的已上架片单） */
export interface MovieDetailView extends ContentDetailView {
  originalTitle?: string;
  director?: string;
  actors?: string;
  region?: string;
  language?: string;
  releaseDate?: string;
  rating?: number;
  posterUrl?: string;
  backdropUrl?: string;
  collections?: MovieCollectionView[];
}

/** 电影片单（GET /video/collections/page 仅返回已上架） */
export interface MovieCollectionView {
  id: number;
  title: string;
  description?: string;
  coverUrl?: string;
  publishState?: PublishState;
  itemCount: number;
  sortNo?: number;
  publishedAt?: string;
  createTime?: string;
}

/** 片单详情（片单内电影按片单排序，仅返回当前可见内容） */
export interface MovieCollectionDetailView extends MovieCollectionView {
  items: MovieView[];
}

/** 榜单（GET /video/rankings）：热门榜与新片榜；contentType 为空表示跨类型 */
export interface RankingView {
  contentType?: ContentType | null;
  hot: PublicContentView[];
  latest: PublicContentView[];
}

/** 首页聚合（GET /video/home） */
export interface HomeView {
  shorts: PublicContentView[];
  videos: PublicContentView[];
  movies: PublicContentView[];
  categories: CategoryView[];
  tags: TagView[];
}

/** 播放票据（POST /video/play/ticket、/video/play/tickets） */
export interface PlayTicket {
  ticket: string;
  expiresIn: number;
  refreshIn: number;
  contentId: number;
  contentType: ContentType;
  title: string;
  durationMs?: number;
  progressMs?: number;
  streamUrl: string;
}

/** 批量票据（不可播放的内容 ID 会出现在 rejectedContentIds） */
export interface PlayTicketBatch {
  tickets: PlayTicket[];
  rejectedContentIds: number[];
}

/** 观看历史（断点续播；内容已删除时 title 为空且 playable=false） */
export interface WatchHistoryView {
  contentId: number;
  contentType: ContentType;
  title?: string;
  coverUrl?: string;
  progressMs?: number;
  durationMs?: number;
  source?: string;
  watchedAt?: string;
  playable: boolean;
}

/** 评论（后端不含昵称与审核状态字段） */
export interface CommentView {
  id: number;
  contentId: number;
  userId: number;
  parentId: number;
  content: string;
  likeCount: number;
  liked: boolean;
  createTime: string;
  replies?: CommentView[];
}

/** 上传声明（GET /video/declaration/current） */
export interface UploadDeclaration {
  version: string;
  title: string;
  text: string;
  accepted: boolean;
  contentSource?: ContentSource;
  acceptedAt?: string;
}

/** 服务运行元信息（GET /video/meta） */
export interface VideoMeta {
  service: string;
  tenantId: number;
  userId?: string;
  declarationVersion: string;
  declarationTitle: string;
  uploadEnabled: boolean;
  contentTypes: ContentType[];
  shortMaxBytes: number;
  videoMaxBytes: number;
  movieMaxBytes: number;
  playTicketTtlSeconds: number;
  playTicketRefreshSeconds: number;
  commentPreAudit: boolean;
}

/** 分页查询参数（关键词 / 分类 / 排序等） */
export interface PageQuery {
  current?: number;
  size?: number;
  keyword?: string;
  categoryId?: number;
  tagId?: number;
  sortBy?: FeedSort;
  visibility?: ContentVisibility;
  publishState?: PublishState;
}

/** 短视频 feed 查询参数 */
export interface FeedQuery {
  sort?: FeedSort;
  categoryId?: number;
  cursor?: string;
  size?: number;
}

/** 内容保存入参（创建 / 编辑短视频与视频共用） */
export interface ContentSaveInput {
  videoFileId: number;
  coverFileId?: number;
  title: string;
  subtitle?: string;
  description?: string;
  durationMs?: number;
  width?: number;
  height?: number;
  sizeBytes?: number;
  categoryId?: number;
  creatorId?: number;
  seriesId?: number;
  episodeNo?: number;
  tagIds?: number[];
  visibility?: ContentVisibility;
  source?: ContentSource;
  publishNow?: boolean;
}

/** 观看进度上报入参（时长由服务端按内容记录校验，无需前端上报） */
export interface PlayProgressInput {
  contentId: number;
  progressMs: number;
  source?: string;
  device?: string;
}

/** 读取服务运行元信息 */
export function fetchMeta(): Promise<VideoMeta> {
  return http.get<VideoMeta>('/video/meta');
}

/** 首页聚合：短视频推荐 / 视频 / 电影 / 分类 / 标签 */
export function fetchHome(): Promise<HomeView> {
  return http.get<HomeView>('/video/home');
}

/** 短视频 feed（最新 / 热门 / 分类，键集游标分页） */
export function fetchShortFeed(query: FeedQuery): Promise<FeedView> {
  return http.get<FeedView>('/video/shorts/feed', { ...query });
}

/** 短视频详情（不可见内容按不存在返回 40401） */
export function fetchShortDetail(id: number): Promise<ContentDetailView> {
  return http.get<ContentDetailView>(`/video/shorts/${id}`);
}

/** 视频分页（仅已发布且可见内容） */
export function fetchVideoPage(query: PageQuery): Promise<{
  records: PublicContentView[];
  total: number;
  current: number;
  size: number;
}> {
  return http.get('/video/videos/page', { ...query });
}

/** 视频详情 */
export function fetchVideoDetail(id: number): Promise<ContentDetailView> {
  return http.get<ContentDetailView>(`/video/videos/${id}`);
}

/** 电影分页（仅已上架且当前用户可见；海报 / 背景图走代理地址） */
export function fetchMoviePage(query: PageQuery): Promise<{
  records: MovieView[];
  total: number;
  current: number;
  size: number;
}> {
  return http.get('/video/movies/page', { ...query });
}

/** 电影详情（含海报 / 背景图代理地址与收录本片的已上架片单） */
export function fetchMovieDetail(id: number): Promise<MovieDetailView> {
  return http.get<MovieDetailView>(`/video/movies/${id}`);
}

/** 已上架片单分页 */
export function fetchCollectionPage(query?: PageQuery): Promise<{
  records: MovieCollectionView[];
  total: number;
  current: number;
  size: number;
}> {
  return http.get('/video/collections/page', { ...query });
}

/** 片单详情（未上架片单按不存在返回 40405） */
export function fetchCollectionDetail(id: number): Promise<MovieCollectionDetailView> {
  return http.get<MovieCollectionDetailView>(`/video/collections/${id}`);
}

/** 榜单：热门榜与新片榜（type 为空表示跨类型，size 默认 10、上限 50） */
export function fetchRankings(query?: { type?: ContentType; size?: number }): Promise<RankingView> {
  return http.get<RankingView>('/video/rankings', { ...query });
}

/** 片单封面代理地址（响应为二进制图片） */
export function buildCollectionCoverUrl(id: number): string {
  return `/video/collections/${id}/cover`;
}

/** 电影海报代理地址（响应为二进制图片） */
export function buildMoviePosterUrl(id: number): string {
  return `/video/movies/${id}/poster`;
}

/** 已上架系列分页（视频列表展示系列入口 / 上传时选择系列） */
export function fetchSeriesPage(query?: PageQuery): Promise<{
  records: SeriesView[];
  total: number;
  current: number;
  size: number;
}> {
  return http.get('/video/series/page', { ...query });
}

/** 系列详情（未上架系列按不存在返回 40404） */
export function fetchSeriesDetail(id: number): Promise<SeriesDetailView> {
  return http.get<SeriesDetailView>(`/video/series/${id}`);
}

/** 系列封面代理地址（响应为二进制图片） */
export function buildSeriesCoverUrl(id: number): string {
  return `/video/series/${id}/cover`;
}

/** 统一内容详情 */
export function fetchContentDetail(id: number): Promise<ContentDetailView> {
  return http.get<ContentDetailView>(`/video/content/${id}`);
}

/** 分类列表（启用分类，前端按 contentTypeScope 过滤） */
export function fetchCategories(): Promise<CategoryView[]> {
  return http.get<CategoryView[]>('/video/categories');
}

/** 标签列表 */
export function fetchTags(): Promise<TagView[]> {
  return http.get<TagView[]>('/video/tags');
}

/** 站内搜索 */
export function searchContent(query: PageQuery & { contentType?: ContentType }): Promise<{
  records: PublicContentView[];
  total: number;
  current: number;
  size: number;
}> {
  return http.get('/video/search', { ...query });
}

/** 签发单个播放票据 */
export function createPlayTicket(contentId: number): Promise<PlayTicket> {
  return http.post<PlayTicket>('/video/play/ticket', { contentId });
}

/** 批量签发播放票据（feed 预取，单次最多 20 个） */
export function createPlayTickets(contentIds: number[]): Promise<PlayTicketBatch> {
  return http.post<PlayTicketBatch>('/video/play/tickets', { contentIds });
}

/** 流式播放地址（只携带短时票据，不含 fileId） */
export function buildStreamUrl(contentId: number, ticket: string): string {
  return `/video/stream/${contentId}?ticket=${encodeURIComponent(ticket)}`;
}

/** 封面代理地址（响应为二进制图片） */
export function buildCoverUrl(contentId: number): string {
  return `/video/content/${contentId}/cover`;
}

/** 观看进度上报（同一内容重复上报幂等） */
export function reportProgress(input: PlayProgressInput): Promise<void> {
  return http.post<void>('/video/play/progress', input);
}

/** 最近观看分页 */
export function fetchHistory(query?: PageQuery): Promise<{
  records: WatchHistoryView[];
  total: number;
  current: number;
  size: number;
}> {
  return http.get('/video/history', { ...query });
}

/** 删除最近观看记录 */
export function deleteHistory(contentId: number): Promise<void> {
  return http.del<void>(`/video/history/${contentId}`);
}

/** 点赞 / 取消点赞（幂等） */
export function likeContent(contentId: number, liked: boolean): Promise<void> {
  return liked
    ? http.post<void>(`/video/content/${contentId}/like`)
    : http.del<void>(`/video/content/${contentId}/like`);
}

/** 收藏 / 取消收藏（幂等） */
export function favoriteContent(contentId: number, favorited: boolean): Promise<void> {
  return favorited
    ? http.post<void>(`/video/content/${contentId}/favorite`)
    : http.del<void>(`/video/content/${contentId}/favorite`);
}

/** 我的点赞分页 */
export function fetchMyLikes(query?: PageQuery): Promise<{
  records: PublicContentView[];
  total: number;
  current: number;
  size: number;
}> {
  return http.get('/video/my/likes', { ...query });
}

/** 我的收藏分页 */
export function fetchMyFavorites(query?: PageQuery): Promise<{
  records: PublicContentView[];
  total: number;
  current: number;
  size: number;
}> {
  return http.get('/video/my/favorites', { ...query });
}

/** 评论分页 */
export function fetchComments(contentId: number, query?: PageQuery): Promise<{
  records: CommentView[];
  total: number;
  current: number;
  size: number;
}> {
  return http.get(`/video/content/${contentId}/comment/page`, { ...query });
}

/** 发表评论（parentId 非空时为回复） */
export function submitComment(
  contentId: number,
  input: { content: string; parentId?: number },
): Promise<number> {
  return http.post<number>(`/video/content/${contentId}/comment`, input);
}

/** 评论点赞 / 取消 */
export function likeComment(commentId: number, liked: boolean): Promise<void> {
  return liked
    ? http.post<void>(`/video/comment/${commentId}/like`)
    : http.del<void>(`/video/comment/${commentId}/like`);
}

/** 当前上传声明与本人签署状态 */
export function fetchDeclaration(): Promise<UploadDeclaration> {
  return http.get<UploadDeclaration>('/video/declaration/current');
}

/** 签署当前上传声明（同一版本重复签署幂等） */
export function acceptDeclaration(version: string, contentSource: ContentSource): Promise<UploadDeclaration> {
  return http.post<UploadDeclaration>('/video/declaration/accept', { version, contentSource });
}

/** 我的短视频分页（含草稿与私密） */
export function fetchMyShorts(query?: PageQuery): Promise<{
  records: OwnerContentView[];
  total: string;
  current: string;
  size: string;
}> {
  return http.get('/video/my/shorts', { ...query });
}

/** 我的视频分页（含草稿与私密） */
export function fetchMyVideos(query?: PageQuery): Promise<{
  records: OwnerContentView[];
  total: string;
  current: string;
  size: string;
}> {
  return http.get('/video/my/videos', { ...query });
}

/** 读取本人作品编辑视图（含 videoFileId / coverFileId，供编辑回填） */
export function fetchOwnerContent(
  id: number,
  contentType: Extract<ContentType, 'SHORT_VIDEO' | 'VIDEO'>,
): Promise<ContentEditView> {
  const path = contentType === 'SHORT_VIDEO' ? 'shorts' : 'videos';
  return http.get<ContentEditView>(`/video/user/${path}/${id}`);
}

/** 创建短视频 */
export function createShortVideo(input: ContentSaveInput): Promise<number> {
  return http.post<number>('/video/user/shorts', input);
}

/** 创建视频 */
export function createVideo(input: ContentSaveInput): Promise<number> {
  return http.post<number>('/video/user/videos', input);
}

/** 编辑本人短视频 */
export function updateShortVideo(id: number, input: ContentSaveInput): Promise<void> {
  return http.put<void>(`/video/user/shorts/${id}`, input);
}

/** 编辑本人视频 */
export function updateVideo(id: number, input: ContentSaveInput): Promise<void> {
  return http.put<void>(`/video/user/videos/${id}`, input);
}

/** 删除本人短视频（逻辑删除） */
export function deleteShortVideo(id: number): Promise<void> {
  return http.del<void>(`/video/user/shorts/${id}`);
}

/** 删除本人视频（逻辑删除） */
export function deleteVideo(id: number): Promise<void> {
  return http.del<void>(`/video/user/videos/${id}`);
}

/** 发布本人短视频 */
export function publishShortVideo(id: number): Promise<void> {
  return http.post<void>(`/video/user/shorts/${id}/publish`);
}

/** 下架本人短视频 */
export function offlineShortVideo(id: number): Promise<void> {
  return http.post<void>(`/video/user/shorts/${id}/offline`);
}

/** 发布本人视频 */
export function publishVideo(id: number): Promise<void> {
  return http.post<void>(`/video/user/videos/${id}/publish`);
}

/** 下架本人视频 */
export function offlineVideo(id: number): Promise<void> {
  return http.post<void>(`/video/user/videos/${id}/offline`);
}
