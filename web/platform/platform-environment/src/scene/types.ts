import type { Scene } from 'three';
import type { SceneOptions } from './options';

/**
 * 场景的类型契约。
 *
 * 与实现（`scenes/`）分开，是为了让"有哪些场景"这件事有**单一的类型来源**：
 * `catalog.ts` 用 `Record<SceneId, SceneDefinition>` 组装注册表，
 * 于是"漏实现一个场景"和"注册了一个不存在的场景"都会在**编译期**报错，
 * 而不是等用户选中之后才发现是白屏。
 */

/**
 * 全部场景的 id。
 *
 * ⚠️ **这里的字面量与目录下的实现文件是一一对应的隐式契约。**
 * 新增场景的正确顺序是：实现 → 进 `scenes/` → 在此加字面量 → 在 `catalog.ts` 注册。
 * 注册表用 `Record<SceneId, …>` 承接，漏任何一步都过不了类型检查。
 */
export type SceneId =
  'ancient-architecture' | 'primeval-jungle' | 'modern-office' | 'spacecraft' | 'baseline-lab';

/**
 * 场景的图标标识。
 *
 * 刻意是一个**枚举而不是 SVG 字符串**：元数据只说"要哪个图标"，
 * 具体画成什么样属于渲染，归组件管。这样元数据始终是纯数据，
 * 将来交给后端字典时也不用担心"后端下发的字符串被当成代码执行"。
 */
export type SceneIcon = 'roof' | 'tree' | 'tower' | 'ship' | 'cube';

/**
 * 场景的**展示元数据**。
 *
 * 只有这些字段是"数据"—— 将来若把清单交付后端字典，能下发的就是这一层。
 * 注意这里**没有一处涉及几何体**：真正决定"古建筑长什么样"的代码在前端，
 * 后端下发不了，也不该下发。
 */
export type SceneMeta = {
  id: SceneId;
  /** 列表中的显示名 */
  title: string;
  /** 一句话说明。选择器卡片上直接展示，所以必须短 */
  summary: string;
  /** 分类标签，如「室外」「室内」「太空」 */
  tags: readonly string[];
  /** 卡片图标标识 */
  icon: SceneIcon;
  /** 排序权重，小者在前。远端可覆盖，因此给数字而不是靠数组顺序 */
  order: number;
  /** 场景主色：选择器卡片的示意块与强调色都用它 */
  accent: string;
  /** 背景色。**同时作为雾色** —— 两者不一致就没有"远处融进背景"的效果 */
  background: string;
  /**
   * 进入场景时的相机位姿。
   *
   * 每个场景都必须自己给：构图差着数量级，共用一套取景必然有场景取不对
   * （古建筑要退到能看全屋顶，飞船要贴到能看清舷窗）。
   */
  camera: {
    position: readonly [number, number, number];
    target: readonly [number, number, number];
  };
  /** 相机可拉近 / 拉远的距离范围 —— 同样随场景尺度走 */
  distance: readonly [number, number];
  /** IBL 环境光强度。室外、太空、室内差别极大，跟着场景走而不是全局一个值 */
  environmentIntensity: number;
  /**
   * 进入本场景时覆盖的渲染选项。
   *
   * 刻意做成 `Partial`：未列出的项沿用全局默认值。作用是让"这个场景里
   * 网格辅助线没意义"这类判断由场景自己声明，而不是让用户每次手动关。
   */
  options?: Partial<SceneOptions>;
};

/**
 * 一个场景的**可运行产物**。
 *
 * 刻意**不含相机**：相机由舞台持有并终身复用，切场景只改位姿。
 * 若让每个场景自带相机，切换时就得改 `OrbitControls.object` 并重置它的内部状态，
 * 那是一条只会引入偶发 bug 的路。
 */
export type SceneBuild = {
  scene: Scene;
  /**
   * 推进一帧。参数是**累计秒数**而不是增量。
   *
   * 要求实现成 `elapsed` 的纯函数：任何一帧的状态都由 `elapsed` 唯一确定。
   * 于是测试不用模拟帧序列，也天然不会因为掉帧出现"转着转着速度变了"。
   */
  update(elapsed: number): void;
  /** 切换线框模式。没有实体网格的场景可以是空实现 */
  setWireframe(enabled: boolean): void;
  /** 切换网格辅助线可见性。地面/太空这类没有地面概念的场景可以是空实现 */
  setGridVisible(visible: boolean): void;
  /** 释放全部几何体与材质。**调用后实例不可再用** */
  dispose(): void;
};

/** 一个场景 = 元数据 + 构建函数。 */
export type SceneDefinition = {
  meta: SceneMeta;
  create(): SceneBuild;
};

/** 清单的来源。界面上如实标注它，而不是让人猜数据从哪来。 */
export type SceneCatalogSource = 'local' | 'remote-merged';

export type SceneCatalog = {
  scenes: readonly SceneDefinition[];
  source: SceneCatalogSource;
};

/**
 * 远端对某条场景元数据的覆盖指令。
 *
 * **护栏 B 的载体：远端只能覆盖，不能发明。**
 * 可覆盖的字段刻意限制在展示层 —— 不含 `camera` / `background` /
 * `environmentIntensity`: 这些与实现强耦合，远端改不了前端代码就不该改取景，
 * 否则一个配置改动就能让画面变得没法看。
 *
 * `id` 故意是 `string` 而非 `SceneId`：远端完全可能给出一条前端没有实现的 id，
 * 这正是必须显式处理的情况（见 `mergeCatalogOverrides`）。
 */
export type SceneMetaOverride = {
  id: string;
  patch?: Partial<Pick<SceneMeta, 'title' | 'summary' | 'tags'>>;
  /** 从清单中隐藏（前端仍有实现，只是不在选择器里露出） */
  hidden?: boolean;
  /** 覆盖排序权重 */
  order?: number;
};
