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
 * 环境（天文与气象）。
 *
 * 与 `minimap` 一样是**纯函数**出口：界面要显示"现在几点、太阳多高"时直接调
 * `sampleEnvironment`，而**不要**去读舞台或另算一遍 —— 同一个 state 永远得到
 * 同一份结果，这正是把它做成纯函数的意义。
 */
export {
  DEFAULT_ENVIRONMENT,
  FLOW_PRESETS,
  advanceEnvironment,
  environmentLook,
  formatClock,
  formatDate,
  normalizeEnvironment,
  sampleEnvironment,
  solarPosition,
  sunDirection,
  toDegrees,
  toRadians,
  type EnvironmentLook,
  type EnvironmentSample,
  type EnvironmentState,
  type SolarPosition,
  type SkyScatter,
} from './env';

/** 场景可选能力的装配件：需要天光的场景用它，不需要的不引 */
export { createAtmosphereRig, type AtmosphereRigConfig } from './atmosphere';

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
  SceneAtmosphere,
  SceneBuild,
  SceneCatalog,
  SceneCatalogSource,
  SceneClimate,
  SceneContext,
  SceneControls,
  SceneDefinition,
  SceneIcon,
  SceneId,
  SceneInteractionSnapshot,
  SceneMeta,
  SceneMetaOverride,
} from './types';
