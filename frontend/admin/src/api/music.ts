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
