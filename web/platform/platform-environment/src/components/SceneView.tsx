import { useEffect, useRef } from 'react';
import { createStage, type SceneOptions, type Stage, type StageStats } from '@/scene';
import './SceneView.less';

type SceneViewProps = {
  options: SceneOptions;
  onStats: (stats: StageStats) => void;
  /** 无法建立 WebGL 上下文时上报，由上层决定怎么显示 */
  onError: (message: string) => void;
};

/**
 * 画布组件：**只负责 three.js 的生命周期**，不懂任何业务。
 *
 * 三条纪律：
 * 1. 舞台只在挂载时创建一次 —— 选项变化走 `setter`，绝不让 effect 依赖选项，
 *    否则拖一次开关就会重建一次 WebGL 上下文；
 * 2. 卸载时必须 `dispose()`；
 * 3. 建立失败要**降级**而不是白屏（无 WebGL 的环境不在少数）。
 *
 * 注意这里**不渲染 `<canvas>`**：画布由 `createStage` 创建、随舞台一起销毁。
 * 原因是 WebGL 上下文被 `forceContextLoss()` 之后，那张画布就永久不可再用了，
 * 而 React 的 StrictMode 在开发期会双跑 effect（建 → 毁 → 建），
 * 组件持有固定画布必然踩到这一点。详见 `scene/stage.ts`。
 */
export default function SceneView({ options, onStats, onError }: SceneViewProps) {
  const hostRef = useRef<HTMLDivElement>(null);
  const stageRef = useRef<Stage | null>(null);

  /**
   * 最新的 props 副本。
   *
   * 建立舞台的 effect 依赖为空数组，它拿到的会是最初那一份闭包；
   * 统计数据每隔半秒就来一次，不能因为回调换了引用就把上下文重建一遍。
   */
  const latestRef = useRef({ options, onStats, onError });
  useEffect(() => {
    latestRef.current = { options, onStats, onError };
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
      const { options: initial } = latestRef.current;

      let stage: Stage;
      try {
        stage = createStage(mountHost, {
          onStats: (stats) => latestRef.current.onStats(stats),
        });
      } catch (error) {
        latestRef.current.onError(error instanceof Error ? error.message : String(error));
        return;
      }

      // 首次选项在这里落地。之后的变化由下面那个 effect 负责 ——
      // 分开写是因为 StrictMode 下本 effect 会重跑，而选项 effect 只依赖选项。
      stage.setAutoRotate(initial.autoRotate);
      stage.setShadows(initial.shadows);
      stage.setWireframe(initial.wireframe);
      stage.setGridVisible(initial.showGrid);
      stage.start();

      stageRef.current = stage;
    }

    boot();

    return () => {
      stageRef.current?.dispose();
      stageRef.current = null;
    };
  }, []);

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

  // 容器本身也是舞台的宿主：画布会被 `createStage` 追加进来，尺寸靠 CSS 铺满。
  return <div className="scene-view" ref={hostRef} />;
}
