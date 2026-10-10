import { useEffect, useRef } from 'react';
import {
  createStage,
  type SceneDefinition,
  type SceneOptions,
  type Stage,
  type StageStats,
} from '@/scene';
import './SceneView.less';

type SceneViewProps = {
  /** 当前场景。变化时舞台内部只换场景图，**不会重建 WebGL 上下文** */
  scene: SceneDefinition;
  options: SceneOptions;
  onStats: (stats: StageStats) => void;
  /** 无法建立 WebGL 上下文时上报，由上层决定怎么显示 */
  onError: (message: string) => void;
};

/**
 * 画布组件：**只负责 three.js 的生命周期**，不懂任何业务。
 *
 * 四条纪律：
 * 1. 舞台只在挂载时创建一次 —— 选项与场景的变化都走 `setter` / `loadScene`，
 *    绝不让建舞台的 effect 依赖它们，否则拖一次开关就会重建一次 WebGL 上下文；
 * 2. 卸载时必须 `dispose()`；
 * 3. 建立失败要**降级**而不是白屏（无 WebGL 的环境不在少数）；
 * 4. 切场景**不等于**重建舞台 —— 渲染器、画布、相机全都复用，
 *    只有场景图被替换。见 `scene/stage.ts` 的 `loadScene`。
 *
 * 注意这里**不渲染 `<canvas>`**：画布由 `createStage` 创建、随舞台一起销毁。
 * 原因是 WebGL 上下文被 `forceContextLoss()` 之后，那张画布就永久不可再用了，
 * 而 React 的 StrictMode 在开发期会双跑 effect（建 → 毁 → 建），
 * 组件持有固定画布必然踩到这一点。详见 `scene/stage.ts`。
 */
export default function SceneView({ scene, options, onStats, onError }: SceneViewProps) {
  const hostRef = useRef<HTMLDivElement>(null);
  const stageRef = useRef<Stage | null>(null);

  /**
   * 最新的 props 副本。
   *
   * 建立舞台的 effect 依赖为空数组，它拿到的会是最初那一份闭包；
   * 统计数据每隔半秒就来一次，不能因为回调换了引用就把上下文重建一遍。
   */
  const latestRef = useRef({ scene, options, onStats, onError });
  useEffect(() => {
    latestRef.current = { scene, options, onStats, onError };
  });

  useEffect(() => {
    const host = hostRef.current;
    if (!host) {
      return;
    }

    /**
     * 再绑定一次带**确定类型**的局部常量。
     *
     * 上面那次 `if` 的类型收窄**不会穿透到下面的嵌套函数**里，
     * 直接在 `boot()` 中用 `host` 会被判成 `HTMLElement | null`。
     */
    const mountHost: HTMLElement = host;

    function boot() {
      const { scene: initialScene, options: initialOptions } = latestRef.current;

      let stage: Stage;
      try {
        stage = createStage(mountHost, {
          scene: initialScene,
          options: initialOptions,
          onStats: (stats) => latestRef.current.onStats(stats),
        });
      } catch (error) {
        latestRef.current.onError(error instanceof Error ? error.message : String(error));
        return;
      }

      stage.start();
      stageRef.current = stage;

      /**
       * 开发期把舞台挂到 window 上，供排查与端到端脚本读取 GPU 资源计数
       * （`stage.diagnostics()`）。
       *
       * 生产构建里 `import.meta.env.DEV` 会被静态求值为 `false`，这整块会被摇掉 ——
       * 所以它不构成对外开放的内部对象。
       */
      if (import.meta.env.DEV) {
        (window as unknown as Record<string, unknown>).__SCENE_STAGE__ = stage;
      }
    }

    boot();

    return () => {
      const stage = stageRef.current;
      stageRef.current = null;
      /**
       * 只在 window 上挂着的仍是**本实例**时才清除。
       *
       * 开发期 StrictMode 会双跑 effect（建 → 毁 → 建），销毁的是第一个实例，
       * 不判断就会把第二个实例的引用一起删掉，之后诊断读到 `undefined`。
       */
      if (
        import.meta.env.DEV &&
        (window as unknown as Record<string, unknown>).__SCENE_STAGE__ === stage
      ) {
        delete (window as unknown as Record<string, unknown>).__SCENE_STAGE__;
      }
      stage?.dispose();
    };
  }, []);

  /** 选项变化：只推开关，不碰场景图。 */
  useEffect(() => {
    const stage = stageRef.current;
    if (!stage) {
      return;
    }
    stage.setAutoRotate(options.autoRotate);
    stage.setShadows(options.shadows);
    stage.setWireframe(options.wireframe);
    stage.setGridVisible(options.showGrid);
  }, [options]);

  /**
   * 场景变化：换场景图。
   *
   * `sceneId` 比对是为了跳过**首次挂载** —— 那时舞台刚由 `createStage` 装好同一个场景，
   * 不比对就会在挂载后立刻多建一次场景图（StrictMode 下更明显）。
   */
  useEffect(() => {
    const stage = stageRef.current;
    if (!stage || stage.sceneId === scene.meta.id) {
      return;
    }
    // 选项一起传进去，让"换场景"和"套用该场景的默认选项"成为一次原子操作 ——
    // 分两步做的话，中间会有一帧是新场景配着旧选项（比如全景线框）渲染出来的
    stage.loadScene(scene, options);
  }, [scene, options]);

  // 容器本身也是舞台的宿主：画布会被 `createStage` 追加进来，尺寸靠 CSS 铺满。
  return <div className="scene-view" ref={hostRef} />;
}
