// 音乐服务 API（经网关 → whale-music /music）
// 运营接口使用运营权限码，用户侧接口使用 sys:music:browse / sys:music:upload。

import { http, type PageResult } from './client';

/** 分页请求公共参数 */
export interface PageQuery {
  current?: number;
  size?: number;
}

/** 发布状态 */
export type PublishState = 'DRAFT' | 'PUBLISHED' | 'OFFLINE';

/** 可见性 */
export type SongVisibility = 'PUBLIC' | 'PRIVATE';

/** 版权状态 */
export type CopyrightState = 'DECLARED' | 'VALID' | 'TAKEDOWN';

/** 标签类型 */
export type TagType = 'GENRE' | 'LANGUAGE' | 'MOOD';

/** 标签 */
export interface TagView {
  id: number;
  name: string;
  tagType?: TagType;
  sortNo?: number;
}

/** 歌手 */
export interface ArtistView {
  id: number;
  name: string;
  avatarFileId?: number;
  region?: string;
  bio?: string;
  status?: number;
  createTime?: string;
  updateTime?: string;
}

/** 专辑 */
export interface AlbumView {
  id: number;
  artistId: number;
  title: string;
  coverFileId?: number;
  releaseDate?: string;
  albumType?: string;
  intro?: string;
  publishState?: PublishState;
  sortNo?: number;
  createTime?: string;
  updateTime?: string;
}

/** 运营侧歌曲视图（含文件 ID，用于编辑回填） */
export interface SongView {
  id: number;
  uploaderId?: number;
  visibility?: SongVisibility;
  albumId?: number;
  artistId?: number;
  title: string;
  subtitle?: string;
  audioFileId?: number;
  coverFileId?: number;
  durationMs?: number;
  lyricFileId?: number;
  lyricText?: string;
  language?: string;
  publishState?: PublishState;
  copyrightState?: CopyrightState;
  playCount?: number;
  likeCount?: number;
  commentCount?: number;
  sortWeight?: number;
  publishedAt?: string;
  takedownReason?: string;
  playable?: boolean;
  tags?: TagView[];
  createTime?: string;
  updateTime?: string;
}

/** 用户侧歌曲视图（不暴露 file_id） */
export interface PublicSongView {
  id: number;
  visibility?: SongVisibility;
  uploaderId?: number;
  albumId?: number;
  artistId?: number;
  title: string;
  subtitle?: string;
  durationMs?: number;
  language?: string;
  coverFileId?: number;
  playCount?: number;
  likeCount?: number;
  commentCount?: number;
  publishedAt?: string;
  playable?: boolean;
  tags?: TagView[];
}

/** 版权无争议协议状态 */
export interface AgreementView {
  agreementVersion: string;
  agreementTitle: string;
  accepted: boolean;
  acceptedAt?: string;
}

/** 服务运行元信息 */
export interface MusicMeta {
  service: string;
  tenantId?: number;
  userId?: string;
  agreementVersion?: string;
  agreementTitle?: string;
  uploadEnabled?: boolean;
  uploadMaxBytes?: number;
  audioFormats?: string[];
  playTicketTtlSeconds?: number;
  commentPreAudit?: boolean;
}

/** 歌曲保存入参（运营与用户上传共用） */
export interface SongSaveInput {
  albumId?: number;
  artistId: number;
  title: string;
  subtitle?: string;
  audioFileId: number;
  coverFileId?: number;
  durationMs?: number;
  lyricFileId?: number;
  lyricText?: string;
  language?: string;
  visibility?: SongVisibility;
  sortWeight?: number;
  tagIds?: number[];
}

/** 歌曲分页查询参数 */
export interface SongQuery extends PageQuery {
  keyword?: string;
  artistId?: number;
  albumId?: number;
  tagId?: number;
  publishState?: PublishState;
  visibility?: SongVisibility;
  sortBy?: 'NEW' | 'HOT';
}

/** 歌手分页查询参数 */
export interface ArtistQuery extends PageQuery {
  keyword?: string;
  status?: number;
}

/** 专辑分页查询参数 */
export interface AlbumQuery extends PageQuery {
  keyword?: string;
  artistId?: number;
  publishState?: PublishState;
}

/** 歌手保存入参 */
export interface ArtistSaveInput {
  name: string;
  avatarFileId?: number;
  region?: string;
  bio?: string;
  status?: number;
}

/** 专辑保存入参 */
export interface AlbumSaveInput {
  artistId: number;
  title: string;
  coverFileId: number;
  releaseDate?: string;
  albumType?: string;
  intro?: string;
  sortNo?: number;
}

/** 标签保存入参 */
export interface TagSaveInput {
  name: string;
  tagType?: TagType;
  sortNo?: number;
}

/** 服务运行元信息 */
export function getMeta(): Promise<MusicMeta> {
  return http.get<MusicMeta>('/music/meta');
}

/** 当前协议版本与签署状态 */
export function getCurrentAgreement(): Promise<AgreementView> {
  return http.get<AgreementView>('/music/copyright/agreement/current');
}

/** 签署当前协议版本（同版本重复签署幂等） */
export function acceptAgreement(agreementVersion: string): Promise<AgreementView> {
  return http.post<AgreementView>('/music/copyright/agreement/accept', {
    agreementVersion,
    agreed: true,
  });
}

/** 全部标签（用户侧筛选复用） */
export function listTags(): Promise<TagView[]> {
  return http.get<TagView[]>('/music/tag/list');
}

/** 歌手分页 */
export function pageArtists(params: ArtistQuery): Promise<PageResult<ArtistView>> {
  return http.get<PageResult<ArtistView>>('/music/artist/page', { ...params });
}

/** 专辑分页 */
export function pageAlbums(params: AlbumQuery): Promise<PageResult<AlbumView>> {
  return http.get<PageResult<AlbumView>>('/music/album/page', { ...params });
}

/** 用户侧公开歌曲分页 */
export function pagePublicSongs(params: SongQuery): Promise<PageResult<PublicSongView>> {
  return http.get<PageResult<PublicSongView>>('/music/public/song/page', { ...params });
}

/** 用户侧公开歌曲详情 */
export function getPublicSong(id: number): Promise<PublicSongView> {
  return http.get<PublicSongView>(`/music/public/song/${id}`);
}

/** 我的作品分页（含自听，返回运营视图便于编辑回填） */
export function pageMySongs(params: SongQuery): Promise<PageResult<SongView>> {
  return http.get<PageResult<SongView>>('/music/my/songs', { ...params });
}

/** 上传歌曲（创建即发布，可见性由入参决定） */
export function uploadSong(input: SongSaveInput): Promise<PublicSongView> {
  return http.post<PublicSongView>('/music/user/song', input);
}

/** 编辑我的作品 */
export function updateMySong(id: number, input: SongSaveInput): Promise<PublicSongView> {
  return http.put<PublicSongView>(`/music/user/song/${id}`, input);
}

/** 删除我的作品 */
export function deleteMySong(id: number): Promise<void> {
  return http.del<void>(`/music/user/song/${id}`);
}

/** 运营歌曲分页（含草稿与自听） */
export function pageSongs(params: SongQuery): Promise<PageResult<SongView>> {
  return http.get<PageResult<SongView>>('/music/song/page', { ...params });
}

/** 运营歌曲详情 */
export function getSong(id: number): Promise<SongView> {
  return http.get<SongView>(`/music/song/${id}`);
}

/** 运营新增歌曲（默认草稿） */
export function createSong(input: SongSaveInput): Promise<SongView> {
  return http.post<SongView>('/music/song', input);
}

/** 运营更新歌曲 */
export function updateSong(id: number, input: SongSaveInput): Promise<SongView> {
  return http.put<SongView>(`/music/song/${id}`, input);
}

/** 运营删除歌曲 */
export function deleteSong(id: number): Promise<void> {
  return http.del<void>(`/music/song/${id}`);
}

/** 上架歌曲 */
export function publishSong(id: number): Promise<SongView> {
  return http.post<SongView>(`/music/song/${id}/publish`);
}

/** 下架歌曲 */
export function offlineSong(id: number): Promise<SongView> {
  return http.post<SongView>(`/music/song/${id}/offline`);
}

/** 版权下架歌曲（强制下线并拒绝播放） */
export function takedownSong(id: number, reason: string): Promise<SongView> {
  return http.post<SongView>(`/music/song/${id}/takedown`, { reason });
}

/** 新增歌手 */
export function createArtist(input: ArtistSaveInput): Promise<ArtistView> {
  return http.post<ArtistView>('/music/artist', input);
}

/** 更新歌手 */
export function updateArtist(id: number, input: ArtistSaveInput): Promise<ArtistView> {
  return http.put<ArtistView>(`/music/artist/${id}`, input);
}

/** 删除歌手 */
export function deleteArtist(id: number): Promise<void> {
  return http.del<void>(`/music/artist/${id}`);
}

/** 新增专辑 */
export function createAlbum(input: AlbumSaveInput): Promise<AlbumView> {
  return http.post<AlbumView>('/music/album', input);
}

/** 更新专辑 */
export function updateAlbum(id: number, input: AlbumSaveInput): Promise<AlbumView> {
  return http.put<AlbumView>(`/music/album/${id}`, input);
}

/** 删除专辑 */
export function deleteAlbum(id: number): Promise<void> {
  return http.del<void>(`/music/album/${id}`);
}

/** 上架专辑 */
export function publishAlbum(id: number): Promise<AlbumView> {
  return http.post<AlbumView>(`/music/album/${id}/publish`);
}

/** 下架专辑 */
export function offlineAlbum(id: number): Promise<AlbumView> {
  return http.post<AlbumView>(`/music/album/${id}/offline`);
}

/** 新增标签 */
export function createTag(input: TagSaveInput): Promise<TagView> {
  return http.post<TagView>('/music/tag', input);
}

/** 更新标签 */
export function updateTag(id: number, input: TagSaveInput): Promise<TagView> {
  return http.put<TagView>(`/music/tag/${id}`, input);
}

/** 删除标签 */
export function deleteTag(id: number): Promise<void> {
  return http.del<void>(`/music/tag/${id}`);
}

/** 播放票据视图（含票据、播放元数据与歌词，不含文件 ID） */
export interface PlayTicketView {
  ticket: string;
  expiresIn: number;
  songId: number;
  title: string;
  artistId?: number;
  coverFileId?: number;
  durationMs?: number;
  lyricText?: string;
  streamUrl: string;
}

/** 播放进度上报入参 */
export interface PlayProgressInput {
  songId: number;
  progressMs: number;
  source?: string;
  device?: string;
}

/** 最近播放条目 */
export interface PlayHistoryView {
  songId: number;
  title?: string;
  artistId?: number;
  coverFileId?: number;
  durationMs?: number;
  progressMs?: number;
  source?: string;
  playedAt?: string;
  playable?: boolean;
}

/** 申请播放票据（绑定当前用户与歌曲，有效期默认 120 秒） */
export function requestPlayTicket(songId: number): Promise<PlayTicketView> {
  return http.post<PlayTicketView>('/music/play/ticket', { songId });
}

/** 上报播放进度（同一歌曲重复上报幂等） */
export function reportPlayProgress(input: PlayProgressInput): Promise<void> {
  return http.post<void>('/music/play/progress', input);
}

/** 最近播放分页 */
export function pagePlayHistory(params: PageQuery): Promise<PageResult<PlayHistoryView>> {
  return http.get<PageResult<PlayHistoryView>>('/music/play/history', { ...params });
}

/** 收藏歌曲（幂等） */
export function likeSong(id: number): Promise<void> {
  return http.post<void>(`/music/song/${id}/like`);
}

/** 取消收藏（幂等） */
export function unlikeSong(id: number): Promise<void> {
  return http.del<void>(`/music/song/${id}/like`);
}

/** 我的收藏分页 */
export function pageMyLikes(params: PageQuery): Promise<PageResult<PublicSongView>> {
  return http.get<PageResult<PublicSongView>>('/music/my/likes', { ...params });
}

/** 歌单可见性 */
export type PlaylistVisibility = 'PUBLIC' | 'PRIVATE';

/** 歌单类型：用户歌单 / 官方歌单 */
export type PlaylistType = 'USER' | 'OFFICIAL';

/** 歌单内歌曲（用户侧歌曲视图 + 歌单内排序号） */
export interface PlaylistSongView extends PublicSongView {
  sortNo?: number;
}

/** 歌单视图（详情响应填充 songs，分页列表不填充） */
export interface PlaylistView {
  id: number;
  /** 所有者用户 ID，官方歌单为 0 */
  ownerId?: number;
  title: string;
  coverFileId?: number;
  description?: string;
  playlistType?: PlaylistType;
  visibility?: PlaylistVisibility;
  songCount?: number;
  playCount?: number;
  createTime?: string;
  updateTime?: string;
  songs?: PlaylistSongView[];
}

/** 歌单保存入参（用户歌单与官方歌单共用） */
export interface PlaylistSaveInput {
  title: string;
  description?: string;
  visibility?: PlaylistVisibility;
  coverFileId?: number;
}

/** 歌单分页查询参数 */
export interface PlaylistQuery extends PageQuery {
  keyword?: string;
  /** 是否只看自己的歌单 */
  mine?: boolean;
}

/** 搜索类型 */
export type SearchType = 'ALL' | 'SONG' | 'ARTIST' | 'ALBUM' | 'PLAYLIST';

/** 站内搜索结果 */
export interface SearchResultView {
  keyword: string;
  type: SearchType;
  total: number;
  songTotal: number;
  artistTotal: number;
  albumTotal: number;
  playlistTotal: number;
  songs?: PublicSongView[];
  artists?: ArtistView[];
  albums?: AlbumView[];
  playlists?: PlaylistView[];
}

/** 首页聚合视图 */
export interface HomeView {
  hotSongs?: PublicSongView[];
  newSongs?: PublicSongView[];
  officialPlaylists?: PlaylistView[];
  tags?: TagView[];
}

/** 创建用户歌单（所有者固定为当前用户） */
export function createPlaylist(input: PlaylistSaveInput): Promise<PlaylistView> {
  return http.post<PlaylistView>('/music/playlist', input);
}

/** 修改自己的歌单 */
export function updatePlaylist(id: number, input: PlaylistSaveInput): Promise<PlaylistView> {
  return http.put<PlaylistView>(`/music/playlist/${id}`, input);
}

/** 删除自己的歌单 */
export function deletePlaylist(id: number): Promise<void> {
  return http.del<void>(`/music/playlist/${id}`);
}

/** 歌单分页：公开歌单（含官方）与本人私有歌单 */
export function pagePlaylists(params: PlaylistQuery): Promise<PageResult<PlaylistView>> {
  return http.get<PageResult<PlaylistView>>('/music/playlist/page', { ...params });
}

/** 歌单详情（含歌曲与排序号） */
export function getPlaylist(id: number): Promise<PlaylistView> {
  return http.get<PlaylistView>(`/music/playlist/${id}`);
}

/** 向歌单加歌（重复加入幂等） */
export function addPlaylistSong(id: number, songId: number): Promise<PlaylistView> {
  return http.post<PlaylistView>(`/music/playlist/${id}/song`, { songId });
}

/** 从歌单移除歌曲（幂等） */
export function removePlaylistSong(id: number, songId: number): Promise<void> {
  return http.del<void>(`/music/playlist/${id}/song/${songId}`);
}

/** 重排歌单内歌曲（songIds 为歌单内全部歌曲的有序列表） */
export function reorderPlaylist(id: number, songIds: number[]): Promise<PlaylistView> {
  return http.put<PlaylistView>(`/music/playlist/${id}/sort`, { songIds });
}

/** 创建官方歌单 */
export function createOfficialPlaylist(input: PlaylistSaveInput): Promise<PlaylistView> {
  return http.post<PlaylistView>('/music/playlist/official', input);
}

/** 修改官方歌单 */
export function updateOfficialPlaylist(
  id: number,
  input: PlaylistSaveInput,
): Promise<PlaylistView> {
  return http.put<PlaylistView>(`/music/playlist/official/${id}`, input);
}

/** 删除官方歌单 */
export function deleteOfficialPlaylist(id: number): Promise<void> {
  return http.del<void>(`/music/playlist/official/${id}`);
}

/** 官方歌单分页 */
export function pageOfficialPlaylists(params: PlaylistQuery): Promise<PageResult<PlaylistView>> {
  return http.get<PageResult<PlaylistView>>('/music/playlist/official/page', { ...params });
}

/** 首页聚合：热门歌曲、新歌、官方歌单与热门标签 */
export function getHome(): Promise<HomeView> {
  return http.get<HomeView>('/music/home');
}

/** 热门榜分页 */
export function getHotChart(params: PageQuery): Promise<PageResult<PublicSongView>> {
  return http.get<PageResult<PublicSongView>>('/music/chart/hot', { ...params });
}

/** 新歌榜分页 */
export function getNewChart(params: PageQuery): Promise<PageResult<PublicSongView>> {
  return http.get<PageResult<PublicSongView>>('/music/chart/new', { ...params });
}

/** 站内搜索（关键词为空时后端返回稳定空结果） */
export function searchMusic(params: { keyword: string; type?: SearchType }): Promise<SearchResultView> {
  return http.get<SearchResultView>('/music/search', { ...params });
}

/** 评论状态：待审核 / 可见 / 已隐藏 */
export type CommentStatus = 'PENDING' | 'VISIBLE' | 'HIDDEN';

/** 歌曲评论视图（用户侧列表仅包含可见评论） */
export interface CommentView {
  id: number;
  songId: number;
  songTitle?: string;
  userId: number;
  parentId: number;
  content: string;
  status?: CommentStatus;
  likeCount?: number;
  createTime?: string;
  replies?: CommentView[];
}

/** 发表评论或回复（parentId 为 0 或不传表示顶级评论） */
export function submitComment(songId: number, content: string, parentId?: number): Promise<CommentView> {
  return http.post<CommentView>(`/music/song/${songId}/comment`, { content, parentId: parentId ?? 0 });
}

/** 歌曲评论分页（顶级评论倒序并携带可见回复） */
export function pageComments(songId: number, params: PageQuery): Promise<PageResult<CommentView>> {
  return http.get<PageResult<CommentView>>(`/music/song/${songId}/comment/page`, { ...params });
}
