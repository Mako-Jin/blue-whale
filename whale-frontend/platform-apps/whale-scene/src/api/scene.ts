// Wave 5.77 城池场景导航前端 API（M2 2D MVP 对齐后端实际契约）
//
// 约定：
// 1. 所有路径均为相对路径，由基座 / Nginx 经网关 8080 代理（网关不剥离 `/scene` 前缀）。
// 2. 导航只读接口登录即可访问；管理接口由 `sys:scene:*` 权限码控制，页面按需显隐操作按钮，
//    服务端仍是唯一的鉴权方。
// 3. 分页统一为 { records, total, current, size }。
// 4. 可见性规则整体替换（PUT /scene/{id}/visibility），请求体为 { rules: [...] }。

import { http } from './client';

/** 场景状态：草稿 / 已发布 / 已下线 */
export type SceneStatus = 'DRAFT' | 'PUBLISHED' | 'OFFLINE';

/** 节点类型：区域 / 建筑 / 入口 */
export type SceneNodeType = 'REGION' | 'BUILDING' | 'ENTRANCE';

/** 入口类型：系统 / 页面 / 知识库 / 外链 */
export type EntryType = 'SYSTEM' | 'PAGE' | 'KNOWLEDGE' | 'EXTERNAL';

/** 可见性主体类型 */
export type VisibilitySubjectType = 'ALL' | 'ROLE' | 'DEPT' | 'USER';

/** 可见性效果（DENY 优先） */
export type VisibilityEffect = 'ALLOW' | 'DENY';

/** 服务能力与预算参数（GET /scene/meta） */
export interface SceneMeta {
  threeDEnabled: boolean;
  maxNodesPerScene: number;
  maxNodeDepth: number;
  navigationNodeLimit: number;
}

/** 视图模式：2D / 3D */
export type SceneViewMode = '2D' | '3D';

/** 用户场景偏好（GET/PUT /scene/preference） */
export interface ScenePreference {
  viewMode: SceneViewMode;
  defaultSceneKey?: string | null;
}

/** 场景列表视图 */
export interface SceneView {
  id: number;
  sceneKey: string;
  name: string;
  description?: string;
  status: SceneStatus;
  layoutVersion?: number | null;
  publishedAt?: string | null;
  createTime?: string;
  updateTime?: string;
}

/** 场景节点视图（管理端） */
export interface SceneNodeView {
  id: number;
  sceneId: number;
  nodeKey: string;
  parentKey?: string | null;
  nodeType: SceneNodeType;
  name: string;
  icon?: string | null;
  x?: number | null;
  y?: number | null;
  z?: number | null;
  sortNo?: number | null;
  status?: string;
}

/** 节点入口绑定视图 */
export interface NodeBindingView {
  id: number;
  nodeId: number;
  entryType: EntryType;
  targetPath: string;
  permissionCode?: string | null;
  enabled?: boolean;
}

/** 可见性规则视图 */
export interface SceneVisibilityView {
  id: number;
  sceneId: number;
  /** 0 表示场景级规则 */
  nodeId: number;
  subjectType: VisibilitySubjectType;
  subjectId?: string | null;
  effect: VisibilityEffect;
}

/** 布局快照（最近一次发布） */
export interface LayoutSnapshotView {
  version: number;
  checksum: string;
  layoutJson: string;
}

/** 场景详情（管理端一次返回节点 / 绑定 / 可见性） */
export interface SceneDetailView {
  scene: SceneView;
  nodes: SceneNodeView[];
  bindings: NodeBindingView[];
  visibilities: SceneVisibilityView[];
  latestLayout?: LayoutSnapshotView | null;
}

/** 导航入口 */
export interface NavigationEntryView {
  entryType: EntryType;
  targetPath: string;
}

/** 导航节点（已按可见性过滤） */
export interface NavigationNodeView {
  nodeKey: string;
  parentKey?: string | null;
  nodeType: SceneNodeType;
  name: string;
  icon?: string | null;
  x?: number | null;
  y?: number | null;
  sortNo?: number | null;
  entries?: NavigationEntryView[];
}

/** 导航场景 */
export interface NavigationSceneView {
  sceneKey: string;
  name: string;
  layoutVersion?: number | null;
  nodes: NavigationNodeView[];
}

/** 场景分页查询参数 */
export interface SceneQuery {
  keyword?: string;
  status?: string;
  current?: number;
  size?: number;
}

/** 场景保存命令 */
export interface SceneSaveCommand {
  sceneKey: string;
  name: string;
  description?: string;
}

/** 节点保存命令（3D 用的 z 字段在 M3 才启用，界面不暴露） */
export interface SceneNodeSaveCommand {
  nodeKey: string;
  parentKey?: string;
  nodeType: SceneNodeType;
  name: string;
  icon?: string;
  x?: number;
  y?: number;
  sortNo?: number;
}

/** 入口绑定命令 */
export interface NodeBindingSaveCommand {
  entryType: EntryType;
  targetPath: string;
  permissionCode?: string;
  enabled?: boolean;
}

/** 单条可见性规则命令 */
export interface SceneVisibilityCommand {
  /** 0 表示场景级规则 */
  nodeId?: number;
  subjectType: VisibilitySubjectType;
  subjectId?: string;
  effect: VisibilityEffect;
}

/** 查询场景服务能力与预算参数 */
export function fetchSceneMeta(): Promise<SceneMeta> {
  return http.get<SceneMeta>('/scene/meta');
}

/** 当前用户可见的场景导航（登录即可） */
export function fetchNavigation(): Promise<NavigationSceneView[]> {
  return http.get<NavigationSceneView[]>('/scene/navigation');
}

/** 场景分页 */
export function fetchScenePage(query: SceneQuery = {}): Promise<{
  records: SceneView[];
  total: number;
  current: number;
  size: number;
}> {
  return http.get('/scene/page', {
    keyword: query.keyword,
    status: query.status,
    current: query.current ?? 1,
    size: query.size ?? 20,
  });
}

/** 场景详情 */
export function fetchSceneDetail(id: number): Promise<SceneDetailView> {
  return http.get<SceneDetailView>(`/scene/${id}`);
}

/** 新建场景（草稿） */
export function createScene(command: SceneSaveCommand): Promise<SceneView> {
  return http.post<SceneView>('/scene', command);
}

/** 编辑场景 */
export function updateScene(id: number, command: SceneSaveCommand): Promise<SceneView> {
  return http.put<SceneView>(`/scene/${id}`, command);
}

/** 发布场景（服务端校验节点树与入口后生成布局快照） */
export function publishScene(id: number): Promise<SceneView> {
  return http.post<SceneView>(`/scene/${id}/publish`);
}

/** 下线场景 */
export function offlineScene(id: number): Promise<SceneView> {
  return http.post<SceneView>(`/scene/${id}/offline`);
}

/** 新增节点 */
export function createSceneNode(
  sceneId: number,
  command: SceneNodeSaveCommand,
): Promise<SceneNodeView> {
  return http.post<SceneNodeView>(`/scene/${sceneId}/node`, command);
}

/** 编辑节点 */
export function updateSceneNode(
  sceneId: number,
  nodeId: number,
  command: SceneNodeSaveCommand,
): Promise<SceneNodeView> {
  return http.put<SceneNodeView>(`/scene/${sceneId}/node/${nodeId}`, command);
}

/** 删除节点（存在子节点时服务端拒绝） */
export function deleteSceneNode(nodeId: number): Promise<void> {
  return http.del<void>(`/scene/node/${nodeId}`);
}

/** 设置节点入口绑定（同一节点重复调用即覆盖） */
export function saveNodeBinding(
  nodeId: number,
  command: NodeBindingSaveCommand,
): Promise<NodeBindingView> {
  return http.put<NodeBindingView>(`/scene/node/${nodeId}/binding`, command);
}

/** 替换场景可见性规则（DENY 优先） */
export function replaceVisibility(
  sceneId: number,
  rules: SceneVisibilityCommand[],
): Promise<SceneVisibilityView[]> {
  return http.put<SceneVisibilityView[]>(`/scene/${sceneId}/visibility`, { rules });
}

/** 查询当前用户场景偏好（无记录时后端返回默认 2D） */
export function fetchScenePreference(): Promise<ScenePreference> {
  return http.get<ScenePreference>('/scene/preference');
}

/** 保存当前用户场景偏好（视图模式与默认场景） */
export function saveScenePreference(preference: ScenePreference): Promise<ScenePreference> {
  return http.put<ScenePreference>('/scene/preference', preference);
}
