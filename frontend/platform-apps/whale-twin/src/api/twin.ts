// 孪生子应用 API 层：场景 CRUD + 实体场景树 + 模型预览取流
//
// 后端统一返回 Result 信封（code===0 成功）；请求走相对路径 /twin/**，
// 由基座 / Nginx 经网关代理（不剥离一级前缀，REQ-ARCH-003）。

import { getToken, http } from './client';

/** 场景视图对象（对齐后端 SceneVO） */
export interface SceneVO {
  id: string;
  code: string;
  name: string;
  description?: string | null;
  thumbnailFileId?: string | null;
  sceneConfig?: string | null;
  version: number;
  status: 'draft' | 'published' | 'disabled';
  entityCount: number;
  createdBy?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
}

/** 场景保存请求 */
export interface SceneSaveRequest {
  code: string;
  name: string;
  description?: string;
  thumbnailFileId?: number | null;
  sceneConfig?: string | null;
}

/** 孪生实体视图对象（children 构成场景树） */
export interface EntityVO {
  id: string;
  sceneId: string;
  entityCode: string;
  name: string;
  parentId?: string | null;
  modelFileId?: string | null;
  transformJson?: string | null;
  extraConfig?: string | null;
  sortOrder: number;
  children: EntityVO[];
}

/** 实体保存请求 */
export interface EntitySaveRequest {
  entityCode: string;
  name: string;
  parentId?: number | null;
  modelFileId?: number | null;
  transformJson?: string | null;
  extraConfig?: string | null;
  sortOrder?: number;
}

/** 分页信封（对齐后端 PageResult） */
export interface Paged<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
}

// ---- 场景 ----

export const sceneApi = {
  page: (query?: Record<string, unknown>) =>
    http.get<Paged<SceneVO>>('/twin/scenes/page', query),
  get: (id: string) => http.get<SceneVO>(`/twin/scenes/${id}`),
  create: (body: SceneSaveRequest) => http.post<SceneVO>('/twin/scenes', body),
  update: (id: string, body: SceneSaveRequest) =>
    http.put<SceneVO>(`/twin/scenes/${id}`, body),
  publish: (id: string) => http.post<SceneVO>(`/twin/scenes/${id}/publish`),
  disable: (id: string) => http.post<SceneVO>(`/twin/scenes/${id}/disable`),
  remove: (id: string) => http.del<void>(`/twin/scenes/${id}`),
};

// ---- 实体（场景树）----

export const entityApi = {
  tree: (sceneId: string) => http.get<EntityVO[]>(`/twin/scenes/${sceneId}/entities/tree`),
  list: (sceneId: string) => http.get<EntityVO[]>(`/twin/scenes/${sceneId}/entities`),
  create: (sceneId: string, body: EntitySaveRequest) =>
    http.post<EntityVO>(`/twin/scenes/${sceneId}/entities`, body),
  update: (sceneId: string, id: string, body: EntitySaveRequest) =>
    http.put<EntityVO>(`/twin/scenes/${sceneId}/entities/${id}`, body),
  remove: (sceneId: string, id: string) =>
    http.del<void>(`/twin/scenes/${sceneId}/entities/${id}`),
};

/**
 * 取模型预览地址（GLB/GLTF 经 whale-file /file/{id}/preview）。
 *
 * <p>预览接口需登录态，three 的 GLTFLoader 无法带请求头，因此由本函数
 * fetch 为 Blob 再转 objectURL；加载完成后调用方须 {@link revokeModelUrl} 释放。</p>
 *
 * @param fileId 文件ID
 * @returns objectURL
 */
export async function fetchModelUrl(fileId: string): Promise<string> {
  const token = getToken();
  const response = await fetch(`/file/${fileId}/preview`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!response.ok) {
    throw new Error(`模型预览拉取失败：HTTP ${response.status}`);
  }
  const blob = await response.blob();
  return URL.createObjectURL(blob);
}

/** 释放 fetchModelUrl 生成的 objectURL */
export function revokeModelUrl(url: string): void {
  URL.revokeObjectURL(url);
}
