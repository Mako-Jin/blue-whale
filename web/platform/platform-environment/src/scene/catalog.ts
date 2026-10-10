import { DEFAULT_SCENE_OPTIONS, type SceneOptions } from './options';
import { ancientArchitecture } from './scenes/ancient-architecture';
import { baselineLab } from './scenes/baseline-lab';
import { modernOffice } from './scenes/modern-office';
import { primevalJungle } from './scenes/primeval-jungle';
import { spacecraft } from './scenes/spacecraft';
import type { SceneCatalog, SceneDefinition, SceneId, SceneMeta, SceneMetaOverride } from './types';

/**
 * 场景注册表。
 *
 * 用 `Record<SceneId, SceneDefinition>` 而不是数组，是为了让**漏注册变成编译错误**：
 * 每在 `SceneId` 里加一个 id，就欠一份实现，少一个都过不了类型检查。
 * 顺带也把"注册了 id 之外的场景"这种拼写错误挡在编译期。
 */

const REGISTRY: Record<SceneId, SceneDefinition> = {
  'ancient-architecture': ancientArchitecture,
  'primeval-jungle': primevalJungle,
  'modern-office': modernOffice,
  spacecraft,
  'baseline-lab': baselineLab,
};

/** 进入应用时默认打开的场景。 */
export const DEFAULT_SCENE_ID: SceneId = 'ancient-architecture';

/** 注册表里全部场景，按 `order` 升序 —— 这个顺序就是选择器里的展示顺序。 */
const SCENE_LIST: readonly SceneDefinition[] = Object.values(REGISTRY).sort(
  (left, right) => left.meta.order - right.meta.order,
);

/** 默认场景。首屏必须**同步**拿得到它，否则第一帧就没东西可渲染。 */
export function defaultScene(): SceneDefinition {
  return REGISTRY[DEFAULT_SCENE_ID];
}

/**
 * 同步取内置清单。
 *
 * 这就是 ADR-0003 客户端底线第 2 条要求的**内置兜底值**：远端配置不可用时，
 * 应用仍须以一个完整、可用的清单正常启动。它不是"过渡实现"——
 * 将来清单改由后端下发，这个函数照样要在，只是从"唯一来源"变成"底板"。
 */
export function localSceneCatalog(): SceneCatalog {
  return { scenes: SCENE_LIST, source: 'local' };
}

/** 按 id 找场景；找不到返回 `null`（**不抛异常** —— id 可能来自远端配置或地址栏）。 */
export function findScene(id: string | null | undefined): SceneDefinition | null {
  if (!id) {
    return null;
  }
  return Object.prototype.hasOwnProperty.call(REGISTRY, id) ? REGISTRY[id as SceneId] : null;
}

/** 把场景自己的选项覆盖叠到全局默认值上，得到"进入该场景时的初始选项"。 */
export function sceneOptions(meta: SceneMeta): SceneOptions {
  return { ...DEFAULT_SCENE_OPTIONS, ...meta.options };
}

/**
 * 取场景清单。
 *
 * ⚠️ **护栏 A：调用方只认这个函数，不认数据来源。**
 * 今天它直接返回内置注册表；将来若清单改由后端字典下发，这里换成
 * `fetch` + `mergeCatalogOverrides` 即可，**所有调用方一行都不用改** ——
 * 这正是它从第一天起就是 `async` 的原因，而不是"以后可能要异步"的早熟设计。
 *
 * 接入远端时**必须保留**本地兜底：ADR-0003 的客户端底线第 2 条写着
 * "配置服务不可用时，应用仍须以内置默认值正常启动"。也就是说这份内置清单
 * 不是过渡方案，它是永久底座。
 */
export async function loadSceneCatalog(): Promise<SceneCatalog> {
  return localSceneCatalog();
}

export type MergeCatalogResult = {
  scenes: SceneDefinition[];
  /**
   * 远端给了、但前端**没有对应实现**的 id。
   *
   * 逐个返回而不是静默丢弃，是为了让调用方能告警。这类不一致意味着
   * 后端已经上线了一条前端还不认识的场景配置，用户选中就是白屏。
   */
  unknownIds: string[];
};

/**
 * 把远端的元数据覆盖合并到本地注册表上。
 *
 * ⚠️ **护栏 B：远端只能覆盖，不能发明。**
 *
 * 允许：改标题/描述/标签、调整排序、隐藏某个场景。
 * **不允许**：凭空造出一个前端没有实现的场景 —— 几何体是代码，后端给不了。
 * 所以对认不出的 id 既不抛错也不补位，只记进 `unknownIds` 交给上层。
 *
 * 另外可覆盖字段刻意限制在展示层（见 `SceneMetaOverride`）：
 * 相机位姿、背景色、环境光强度都与实现强耦合，远端改不了实现就不该改取景。
 *
 * 当前**没有生产调用方**（清单还只有本地一个来源）。提前把它写出来的理由是：
 * 它定义的是未来接线时的行为边界，等到接线那一刻才临时决定，
 * 几乎必然决定成"远端给什么就信什么"，而那条路通向白屏。
 */
export function mergeCatalogOverrides(
  local: readonly SceneDefinition[],
  overrides: readonly SceneMetaOverride[],
): MergeCatalogResult {
  const known = new Map<string, SceneDefinition>(local.map((item) => [item.meta.id, item]));
  const patches = new Map<string, Partial<SceneMeta>>();
  const hidden = new Set<string>();
  const unknownIds: string[] = [];

  overrides.forEach((override) => {
    if (!known.has(override.id)) {
      unknownIds.push(override.id);
      return;
    }
    if (override.hidden) {
      hidden.add(override.id);
    }
    const merged: Partial<SceneMeta> = { ...(patches.get(override.id) ?? {}) };
    if (override.patch) {
      Object.assign(merged, override.patch);
    }
    if (typeof override.order === 'number') {
      merged.order = override.order;
    }
    patches.set(override.id, merged);
  });

  const scenes = local
    .filter((definition) => !hidden.has(definition.meta.id))
    .map((definition) => {
      const patch = patches.get(definition.meta.id);
      if (!patch || Object.keys(patch).length === 0) {
        return definition;
      }
      // `create` 原样带过：元数据可以换，实现只认本地这一份
      return { meta: { ...definition.meta, ...patch }, create: definition.create };
    })
    .sort((left, right) => left.meta.order - right.meta.order);

  return { scenes, unknownIds };
}
