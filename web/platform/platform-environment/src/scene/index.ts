export {
  createStage,
  type Stage,
  type StageDiagnostics,
  type StageOptions,
  type StageStats,
} from './stage';
export { DEFAULT_MAX_PIXEL_RATIO, resolveViewport, type Viewport } from './viewport';
export { DEFAULT_SCENE_OPTIONS, type SceneOptions } from './options';

/**
 * 小地图的投影与几何。
 *
 * 全是纯函数，所以小地图"哪边是北、比例对不对"这些最容易画反的地方
 * 可以在无 GPU 环境里断言；组件只负责把它们画到 canvas 上。
 */
export {
  arrowPolygon,
  createMinimapProjection,
  mapDirection,
  viewWedgePolygon,
  type MapPoint,
  type MinimapProjection,
} from './minimap';

/**
 * 场景注册表相关。
 *
 * 组件取清单只允许走 `localSceneCatalog()`（首屏同步兜底）与
 * `loadSceneCatalog()`（最终来源），**不要直接引用注册表内部** ——
 * 那是护栏 A：数据来源必须可替换，否则将来接后端字典时每一处引用都要改。
 */
export {
  DEFAULT_SCENE_ID,
  defaultScene,
  findScene,
  loadSceneCatalog,
  localSceneCatalog,
  mergeCatalogOverrides,
  sceneOptions,
  type MergeCatalogResult,
} from './catalog';

export type {
  MinimapFeatures,
  MinimapRect,
  SceneBuild,
  SceneCatalog,
  SceneCatalogSource,
  SceneContext,
  SceneControls,
  SceneDefinition,
  SceneIcon,
  SceneId,
  SceneInteractionSnapshot,
  SceneMeta,
  SceneMetaOverride,
} from './types';
