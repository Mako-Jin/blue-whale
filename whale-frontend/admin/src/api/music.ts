// 音乐运营 API（经网关 → whale-music /music）
// 权限码：sys:music:artist:* / album:* / song:* / tag:manage / browse

import { del, get, post, put, type PageResult } from './client';

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
  id: string;
  name: string;
  tagType?: TagType;
  sortNo?: number;
}

/** 歌手 */
export interface ArtistView {
  id: string;
  name: string;
  avatarFileId?: string;
  region?: string;
  bio?: string;
  status?: number;
  createTime?: string;
  updateTime?: string;
}

/** 专辑 */
export interface AlbumView {
  id: string;
  artistId: string;
  title: string;
  coverFileId?: string;
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
  id: string;
  uploaderId?: string;
  visibility?: SongVisibility;
  albumId?: string;
  artistId?: string;
  title: string;
  subtitle?: string;
  audioFileId?: string;
  coverFileId?: string;
  durationMs?: number;
  lyricFileId?: string;
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

/** 歌曲分页查询参数 */
export interface SongQuery extends PageQuery {
  keyword?: string;
  artistId?: string;
  albumId?: string;
  tagId?: string;
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
  artistId?: string;
  publishState?: PublishState;
}

/** 歌曲保存入参 */
export interface SongSaveInput {
  albumId?: string;
  artistId: string;
  title: string;
  subtitle?: string;
  audioFileId: string;
  coverFileId?: string;
  durationMs?: number;
  lyricFileId?: string;
  lyricText?: string;
  language?: string;
  visibility?: SongVisibility;
  sortWeight?: number;
  tagIds?: string[];
}

/** 歌手保存入参 */
export interface ArtistSaveInput {
  name: string;
  avatarFileId?: string;
  region?: string;
  bio?: string;
  status?: number;
}

/** 专辑保存入参 */
export interface AlbumSaveInput {
  artistId: string;
  title: string;
  coverFileId: string;
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

/** 歌曲分页（含草稿与自听） */
export function pageSongs(params: SongQuery): Promise<PageResult<SongView>> {
  return get<PageResult<SongView>>('/music/v1/song/page', { ...params });
}

/** 歌曲详情 */
export function getSong(id: string): Promise<SongView> {
  return get<SongView>(`/music/v1/song/${id}`);
}

/** 新增歌曲（默认草稿） */
export function createSong(input: SongSaveInput): Promise<SongView> {
  return post<SongView>('/music/v1/song', input);
}

/** 更新歌曲 */
export function updateSong(id: string, input: SongSaveInput): Promise<SongView> {
  return put<SongView>(`/music/v1/song/${id}`, input);
}

/** 删除歌曲 */
export function deleteSong(id: string): Promise<void> {
  return del<void>(`/music/v1/song/${id}`);
}

/** 上架歌曲 */
export function publishSong(id: string): Promise<SongView> {
  return post<SongView>(`/music/v1/song/${id}/publish`);
}

/** 下架歌曲 */
export function offlineSong(id: string): Promise<SongView> {
  return post<SongView>(`/music/v1/song/${id}/offline`);
}

/** 版权下架歌曲 */
export function takedownSong(id: string, reason: string): Promise<SongView> {
  return post<SongView>(`/music/v1/song/${id}/takedown`, { reason });
}

/** 歌手分页 */
export function pageArtists(params: ArtistQuery): Promise<PageResult<ArtistView>> {
  return get<PageResult<ArtistView>>('/music/v1/artist/page', { ...params });
}

/** 新增歌手 */
export function createArtist(input: ArtistSaveInput): Promise<ArtistView> {
  return post<ArtistView>('/music/v1/artist', input);
}

/** 更新歌手 */
export function updateArtist(id: string, input: ArtistSaveInput): Promise<ArtistView> {
  return put<ArtistView>(`/music/v1/artist/${id}`, input);
}

/** 删除歌手 */
export function deleteArtist(id: string): Promise<void> {
  return del<void>(`/music/v1/artist/${id}`);
}

/** 专辑分页 */
export function pageAlbums(params: AlbumQuery): Promise<PageResult<AlbumView>> {
  return get<PageResult<AlbumView>>('/music/v1/album/page', { ...params });
}

/** 新增专辑 */
export function createAlbum(input: AlbumSaveInput): Promise<AlbumView> {
  return post<AlbumView>('/music/v1/album', input);
}

/** 更新专辑 */
export function updateAlbum(id: string, input: AlbumSaveInput): Promise<AlbumView> {
  return put<AlbumView>(`/music/v1/album/${id}`, input);
}

/** 删除专辑 */
export function deleteAlbum(id: string): Promise<void> {
  return del<void>(`/music/v1/album/${id}`);
}

/** 上架专辑 */
export function publishAlbum(id: string): Promise<AlbumView> {
  return post<AlbumView>(`/music/v1/album/${id}/publish`);
}

/** 下架专辑 */
export function offlineAlbum(id: string): Promise<AlbumView> {
  return post<AlbumView>(`/music/v1/album/${id}/offline`);
}

/** 全部标签 */
export function listTags(): Promise<TagView[]> {
  return get<TagView[]>('/music/v1/tag/list');
}

/** 新增标签 */
export function createTag(input: TagSaveInput): Promise<TagView> {
  return post<TagView>('/music/v1/tag', input);
}

/** 更新标签 */
export function updateTag(id: string, input: TagSaveInput): Promise<TagView> {
  return put<TagView>(`/music/v1/tag/${id}`, input);
}

/** 删除标签 */
export function deleteTag(id: string): Promise<void> {
  return del<void>(`/music/v1/tag/${id}`);
}

/** 歌单可见性 */
export type PlaylistVisibility = 'PUBLIC' | 'PRIVATE';

/** 歌单类型 */
export type PlaylistType = 'USER' | 'OFFICIAL';

/** 用户侧歌曲视图（歌单曲目接口返回，不含文件 ID） */
export interface PublicSongView {
  id: string;
  visibility?: SongVisibility;
  uploaderId?: string;
  albumId?: string;
  artistId?: string;
  title: string;
  subtitle?: string;
  durationMs?: number;
  language?: string;
  coverFileId?: string;
  playCount?: number;
  likeCount?: number;
  commentCount?: number;
  publishedAt?: string;
  playable?: boolean;
  tags?: TagView[];
}

/** 歌单内歌曲（含歌单内排序号） */
export interface PlaylistSongView extends PublicSongView {
  sortNo?: number;
}

/** 歌单视图（详情响应填充 songs） */
export interface PlaylistView {
  id: string;
  ownerId?: string;
  title: string;
  coverFileId?: string;
  description?: string;
  playlistType?: PlaylistType;
  visibility?: PlaylistVisibility;
  songCount?: number;
  playCount?: number;
  createTime?: string;
  updateTime?: string;
  songs?: PlaylistSongView[];
}

/** 歌单保存入参 */
export interface PlaylistSaveInput {
  title: string;
  description?: string;
  visibility?: PlaylistVisibility;
  coverFileId?: string;
}

/** 歌单分页查询参数 */
export interface PlaylistQuery extends PageQuery {
  keyword?: string;
  mine?: boolean;
}

/** 官方歌单分页 */
export function pageOfficialPlaylists(params: PlaylistQuery): Promise<PageResult<PlaylistView>> {
  return get<PageResult<PlaylistView>>('/music/v1/playlist/official/page', { ...params });
}

/** 评论状态：待审核 / 可见 / 已隐藏 */
export type CommentStatus = 'PENDING' | 'VISIBLE' | 'HIDDEN';

/** 歌曲评论视图（审核侧） */
export interface CommentView {
  id: string;
  songId: string;
  songTitle?: string;
  userId: string;
  parentId: string;
  content: string;
  status?: CommentStatus;
  likeCount?: number;
  createTime?: string;
}

/** 评论审核分页查询参数 */
export interface CommentAuditQuery extends PageQuery {
  songId?: string;
  status?: CommentStatus;
  keyword?: string;
}

/** 评论审核分页 */
export function pageCommentAudit(params: CommentAuditQuery): Promise<PageResult<CommentView>> {
  return get<PageResult<CommentView>>('/music/v1/comment/page', { ...params });
}

/** 审核通过（PENDING / HIDDEN → VISIBLE，幂等） */
export function approveComment(id: string): Promise<CommentView> {
  return post<CommentView>(`/music/v1/comment/${id}/approve`);
}

/** 隐藏评论（→ HIDDEN，幂等，立即对用户不可见） */
export function hideComment(id: string): Promise<CommentView> {
  return post<CommentView>(`/music/v1/comment/${id}/hide`);
}

/** 创建官方歌单 */
export function createOfficialPlaylist(input: PlaylistSaveInput): Promise<PlaylistView> {
  return post<PlaylistView>('/music/v1/playlist/official', input);
}

/** 修改官方歌单 */
export function updateOfficialPlaylist(
  id: string,
  input: PlaylistSaveInput,
): Promise<PlaylistView> {
  return put<PlaylistView>(`/music/v1/playlist/official/${id}`, input);
}

/** 删除官方歌单 */
export function deleteOfficialPlaylist(id: string): Promise<void> {
  return del<void>(`/music/v1/playlist/official/${id}`);
}

/** 歌单详情（含歌曲与排序号） */
export function getPlaylist(id: string): Promise<PlaylistView> {
  return get<PlaylistView>(`/music/v1/playlist/${id}`);
}

/** 向歌单加歌（重复加入幂等） */
export function addPlaylistSong(id: string, songId: string): Promise<PlaylistView> {
  return post<PlaylistView>(`/music/v1/playlist/${id}/song`, { songId });
}

/** 从歌单移除歌曲（幂等） */
export function removePlaylistSong(id: string, songId: string): Promise<void> {
  return del<void>(`/music/v1/playlist/${id}/song/${songId}`);
}

/** 重排歌单内歌曲 */
export function reorderPlaylist(id: string, songIds: string[]): Promise<PlaylistView> {
  return put<PlaylistView>(`/music/v1/playlist/${id}/sort`, { songIds });
}

/** 官方歌单加歌（运营） */
export function addOfficialPlaylistSong(id: string, songId: string): Promise<PlaylistView> {
  return post<PlaylistView>(`/music/v1/playlist/official/${id}/song`, { songId });
}

/** 官方歌单移除歌曲（运营，幂等） */
export function removeOfficialPlaylistSong(id: string, songId: string): Promise<void> {
  return del<void>(`/music/v1/playlist/official/${id}/song/${songId}`);
}

/** 官方歌单重排（运营） */
export function reorderOfficialPlaylist(id: string, songIds: string[]): Promise<PlaylistView> {
  return put<PlaylistView>(`/music/v1/playlist/official/${id}/sort`, { songIds });
}

/** 版权申诉状态：待复核 / 已通过 / 已驳回 */
export type AppealStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

/** 版权下架申诉视图（运营复核侧） */
export interface AppealView {
  id: string;
  songId: string;
  songTitle?: string;
  applicantId: string;
  reason: string;
  status: AppealStatus;
  reviewOpinion?: string;
  reviewerId?: string;
  reviewedAt?: string;
  createTime?: string;
}

/** 申诉复核分页查询参数 */
export interface AppealQuery extends PageQuery {
  songId?: string;
  status?: AppealStatus;
}

/** 申诉复核分页（权限码 sys:music:appeal:audit） */
export function pageAppeals(params: AppealQuery): Promise<PageResult<AppealView>> {
  return get<PageResult<AppealView>>('/music/v1/appeal/page', { ...params });
}

/** 通过申诉：版权恢复为有效并保持下架，需运营随后显式重新上架 */
export function approveAppeal(id: string, opinion?: string): Promise<AppealView> {
  return post<AppealView>(`/music/v1/appeal/${id}/approve`, { opinion });
}

/** 驳回申诉：歌曲保持版权下架 */
export function rejectAppeal(id: string, opinion?: string): Promise<AppealView> {
  return post<AppealView>(`/music/v1/appeal/${id}/reject`, { opinion });
}
