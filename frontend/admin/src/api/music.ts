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

/** 歌曲保存入参 */
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

/** 歌曲分页（含草稿与自听） */
export function pageSongs(params: SongQuery): Promise<PageResult<SongView>> {
  return get<PageResult<SongView>>('/music/song/page', { ...params });
}

/** 歌曲详情 */
export function getSong(id: number): Promise<SongView> {
  return get<SongView>(`/music/song/${id}`);
}

/** 新增歌曲（默认草稿） */
export function createSong(input: SongSaveInput): Promise<SongView> {
  return post<SongView>('/music/song', input);
}

/** 更新歌曲 */
export function updateSong(id: number, input: SongSaveInput): Promise<SongView> {
  return put<SongView>(`/music/song/${id}`, input);
}

/** 删除歌曲 */
export function deleteSong(id: number): Promise<void> {
  return del<void>(`/music/song/${id}`);
}

/** 上架歌曲 */
export function publishSong(id: number): Promise<SongView> {
  return post<SongView>(`/music/song/${id}/publish`);
}

/** 下架歌曲 */
export function offlineSong(id: number): Promise<SongView> {
  return post<SongView>(`/music/song/${id}/offline`);
}

/** 版权下架歌曲 */
export function takedownSong(id: number, reason: string): Promise<SongView> {
  return post<SongView>(`/music/song/${id}/takedown`, { reason });
}

/** 歌手分页 */
export function pageArtists(params: ArtistQuery): Promise<PageResult<ArtistView>> {
  return get<PageResult<ArtistView>>('/music/artist/page', { ...params });
}

/** 新增歌手 */
export function createArtist(input: ArtistSaveInput): Promise<ArtistView> {
  return post<ArtistView>('/music/artist', input);
}

/** 更新歌手 */
export function updateArtist(id: number, input: ArtistSaveInput): Promise<ArtistView> {
  return put<ArtistView>(`/music/artist/${id}`, input);
}

/** 删除歌手 */
export function deleteArtist(id: number): Promise<void> {
  return del<void>(`/music/artist/${id}`);
}

/** 专辑分页 */
export function pageAlbums(params: AlbumQuery): Promise<PageResult<AlbumView>> {
  return get<PageResult<AlbumView>>('/music/album/page', { ...params });
}

/** 新增专辑 */
export function createAlbum(input: AlbumSaveInput): Promise<AlbumView> {
  return post<AlbumView>('/music/album', input);
}

/** 更新专辑 */
export function updateAlbum(id: number, input: AlbumSaveInput): Promise<AlbumView> {
  return put<AlbumView>(`/music/album/${id}`, input);
}

/** 删除专辑 */
export function deleteAlbum(id: number): Promise<void> {
  return del<void>(`/music/album/${id}`);
}

/** 上架专辑 */
export function publishAlbum(id: number): Promise<AlbumView> {
  return post<AlbumView>(`/music/album/${id}/publish`);
}

/** 下架专辑 */
export function offlineAlbum(id: number): Promise<AlbumView> {
  return post<AlbumView>(`/music/album/${id}/offline`);
}

/** 全部标签 */
export function listTags(): Promise<TagView[]> {
  return get<TagView[]>('/music/tag/list');
}

/** 新增标签 */
export function createTag(input: TagSaveInput): Promise<TagView> {
  return post<TagView>('/music/tag', input);
}

/** 更新标签 */
export function updateTag(id: number, input: TagSaveInput): Promise<TagView> {
  return put<TagView>(`/music/tag/${id}`, input);
}

/** 删除标签 */
export function deleteTag(id: number): Promise<void> {
  return del<void>(`/music/tag/${id}`);
}

/** 歌单可见性 */
export type PlaylistVisibility = 'PUBLIC' | 'PRIVATE';

/** 歌单类型 */
export type PlaylistType = 'USER' | 'OFFICIAL';

/** 用户侧歌曲视图（歌单曲目接口返回，不含文件 ID） */
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

/** 歌单内歌曲（含歌单内排序号） */
export interface PlaylistSongView extends PublicSongView {
  sortNo?: number;
}

/** 歌单视图（详情响应填充 songs） */
export interface PlaylistView {
  id: number;
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

/** 歌单保存入参 */
export interface PlaylistSaveInput {
  title: string;
  description?: string;
  visibility?: PlaylistVisibility;
  coverFileId?: number;
}

/** 歌单分页查询参数 */
export interface PlaylistQuery extends PageQuery {
  keyword?: string;
  mine?: boolean;
}

/** 官方歌单分页 */
export function pageOfficialPlaylists(params: PlaylistQuery): Promise<PageResult<PlaylistView>> {
  return get<PageResult<PlaylistView>>('/music/playlist/official/page', { ...params });
}

/** 创建官方歌单 */
export function createOfficialPlaylist(input: PlaylistSaveInput): Promise<PlaylistView> {
  return post<PlaylistView>('/music/playlist/official', input);
}

/** 修改官方歌单 */
export function updateOfficialPlaylist(
  id: number,
  input: PlaylistSaveInput,
): Promise<PlaylistView> {
  return put<PlaylistView>(`/music/playlist/official/${id}`, input);
}

/** 删除官方歌单 */
export function deleteOfficialPlaylist(id: number): Promise<void> {
  return del<void>(`/music/playlist/official/${id}`);
}

/** 歌单详情（含歌曲与排序号） */
export function getPlaylist(id: number): Promise<PlaylistView> {
  return get<PlaylistView>(`/music/playlist/${id}`);
}

/** 向歌单加歌（重复加入幂等） */
export function addPlaylistSong(id: number, songId: number): Promise<PlaylistView> {
  return post<PlaylistView>(`/music/playlist/${id}/song`, { songId });
}

/** 从歌单移除歌曲（幂等） */
export function removePlaylistSong(id: number, songId: number): Promise<void> {
  return del<void>(`/music/playlist/${id}/song/${songId}`);
}

/** 重排歌单内歌曲 */
export function reorderPlaylist(id: number, songIds: number[]): Promise<PlaylistView> {
  return put<PlaylistView>(`/music/playlist/${id}/sort`, { songIds });
}

/** 官方歌单加歌（运营） */
export function addOfficialPlaylistSong(id: number, songId: number): Promise<PlaylistView> {
  return post<PlaylistView>(`/music/playlist/official/${id}/song`, { songId });
}

/** 官方歌单移除歌曲（运营，幂等） */
export function removeOfficialPlaylistSong(id: number, songId: number): Promise<void> {
  return del<void>(`/music/playlist/official/${id}/song/${songId}`);
}

/** 官方歌单重排（运营） */
export function reorderOfficialPlaylist(id: number, songIds: number[]): Promise<PlaylistView> {
  return put<PlaylistView>(`/music/playlist/official/${id}/sort`, { songIds });
}
