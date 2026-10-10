import { PMREMGenerator, Timer, type Material, type WebGLRenderTarget } from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';
import { createRenderer } from './renderer';
import { resolveViewport, type Viewport } from './viewport';
import { createWorld, disposeSubtree } from './world';

/**
 * 舞台：把场景图、渲染器、相机控制器和一个动画循环装在一起，对外只暴露
 * `start()` 与一组开关、一个 `dispose()`。
 *
 * React 组件只需要管两件事：**挂载时建、卸载时毁**。所有和帧、和 GPU 打交道的
 * 细节都封在这里，组件不会因为多写一个 effect 就漏掉一次释放。
 */

export type StageStats = {
  fps: number;
  /** 绘制缓冲尺寸（CSS 像素） */
  width: number;
  height: number;
  pixelRatio: number;
  /** 每帧的绘制调用数 —— 判断"场景重不重"最直接的数字 */
  drawCalls: number;
  triangles: number;
};

export type StageOptions = {
  onStats?: (stats: StageStats) => void;
  /** 统计回调的最小间隔（毫秒），默认 500 */
  statsInterval?: number;
};

export type Stage = {
  start(): void;
  /** 相机绕场景自动环绕 */
  setAutoRotate(enabled: boolean): void;
  setShadows(enabled: boolean): void;
  setWireframe(enabled: boolean): void;
  setGridVisible(visible: boolean): void;
  /** 停止循环并释放全部 GPU 资源。**调用后实例不可再用。** */
  dispose(): void;
};

const DEFAULT_STATS_INTERVAL = 500;

/**
 * 画布类名。
 *
 * 画布由本模块创建（原因见 `createStage`），但它的样式属于组件 ——
 * 两边靠这个常量对齐，改名时 `components/SceneView.less` 要一起改。
 */
const CANVAS_CLASS = 'scene-view__canvas';

export function createStage(host: HTMLElement, options: StageOptions = {}): Stage {
  const statsInterval = options.statsInterval ?? DEFAULT_STATS_INTERVAL;

  /**
   * 画布在这里创建，**不接受外部传入**。
   *
   * 起因是 WebGL 的一条硬约束：`dispose()` 里的 `forceContextLoss()` 会把这张
   * canvas 的上下文**永久**标记为 lost，此后 `getShaderPrecisionFormat()` 一律返回
   * `null`，在同一个 canvas 上再 new 一个 `WebGLRenderer` 会直接抛
   * `Cannot read properties of null (reading 'precision')`（three 在
   * `WebGLCapabilities` 里读这个值）。
   *
   * 而"同一个 canvas 被复用"在 React 下是常态：开发期 StrictMode 会**双跑 effect**
   * （建 → 毁 → 建），组件若用 JSX 持有一张固定的 `<canvas>`，第二次就撞上这个错，
   * 表现是"一进页面就显示无法创建 WebGL 上下文"。把画布的创建收进舞台、
   * 让画布与上下文同生共死，这种复用就不可能发生。
   */
  const canvas = host.ownerDocument.createElement('canvas');
  canvas.className = CANVAS_CLASS;
  host.appendChild(canvas);

  const world = createWorld();
  const { renderer, resize, dispose: disposeRenderer } = createRenderer(canvas);

  /**
   * 环境贴图（IBL）。
   *
   * 没有它，`MeshStandardMaterial` 的金属部分没有东西可反射，会渲染成一团死黑 ——
   * 这是"用了 PBR 材质但看起来比 Phong 还差"最常见的原因。
   * `RoomEnvironment` 是 three 自带的一个程序化室内环境，不需要外部 HDRI 文件。
   */
  const pmrem = new PMREMGenerator(renderer);
  const roomEnvironment = new RoomEnvironment();
  const environmentTarget: WebGLRenderTarget = pmrem.fromScene(roomEnvironment, 0.04);
  world.scene.environment = environmentTarget.texture;
  // 环境光只是补细节，强度压在主光之下，否则画面会变得又平又亮
  world.scene.environmentIntensity = 0.35;
  // 两个都不在场景图里，traverse 找不到它们，必须各自释放
  pmrem.dispose();
  disposeSubtree(roomEnvironment);

  const controls = new OrbitControls(world.camera, canvas);
  controls.target.set(0, 1.8, 0);
  controls.enableDamping = true;
  controls.dampingFactor = 0.06;
  controls.autoRotateSpeed = 0.6;
  controls.minDistance = 6;
  controls.maxDistance = 48;
  // 限制俯仰角上限，相机就不会钻到地面以下 —— 从地底看场景没有意义，还容易迷向
  controls.maxPolarAngle = Math.PI / 2 - 0.03;
  controls.update();

  let currentViewport: Viewport | null = null;

  /** 按容器实际尺寸同步相机与绘制缓冲。尺寸没变就直接返回，避免无意义的每帧重算。 */
  function applyViewport(): void {
    const next = resolveViewport(host.getBoundingClientRect(), window.devicePixelRatio);

    if (
      currentViewport &&
      currentViewport.width === next.width &&
      currentViewport.height === next.height &&
      currentViewport.pixelRatio === next.pixelRatio
    ) {
      return;
    }
    currentViewport = next;

    world.camera.aspect = next.width / next.height;
    world.camera.updateProjectionMatrix();
    resize(next);
  }

  /**
   * 观察**容器**而不是 window。
   *
   * 子应用被基座加载时，窗口大小没变但内容区可能因为基座布局变化而变窄/变高，
   * 只听 window.resize 会漏掉这种变化，画面被拉伸。ResizeObserver 两种情形都覆盖。
   */
  const hasResizeObserver = typeof ResizeObserver !== 'undefined';
  const observer = hasResizeObserver ? new ResizeObserver(() => applyViewport()) : null;
  observer?.observe(host);
  // 没有 ResizeObserver 的环境（老浏览器 / 测试替身）退回监听 window
  if (!hasResizeObserver) {
    window.addEventListener('resize', applyViewport);
  }

  /**
   * `Timer` 是 three 核心里的计时器，r183 起取代 `Clock`（后者已弃用）。
   * 用它而不是自己拿 `performance.now()` 相减，是因为它多管两件容易漏的事：
   *
   * 1. `update()` 每帧推进一次内部状态，`getDelta()` / `getElapsed()` 在同一帧内
   *    **可以反复读且值一致** —— 自己算的话，多个消费者各读一次时间就会互相干扰；
   * 2. `connect(document)` 借 Page Visibility API 兜住"切到别的标签页再切回来"：
   *    不做这件事，回来那一帧的 delta 是整段挂起时长，物体会瞬移一大截。
   *
   * 精度是秒，与 `Clock` 一致，所以 `controls.update(delta)` 的语义不变。
   */
  const timer = new Timer();
  timer.connect(host.ownerDocument);

  let rafId = 0;
  let running = false;
  let framesInWindow = 0;
  let statsWindowStart = 0;

  function frame(timestamp: number): void {
    // 先排下一帧：这样即便本帧抛错也不会把循环掐死
    rafId = requestAnimationFrame(frame);

    timer.update(timestamp);
    world.update(timer.getElapsed());
    // 阻尼与自动环绕都依赖 deltaTime，传进去才能做到"掉帧时速度不变"
    controls.update(timer.getDelta());
    renderer.render(world.scene, world.camera);

    framesInWindow += 1;
    const windowElapsed = timestamp - statsWindowStart;
    if (windowElapsed >= statsInterval) {
      options.onStats?.({
        fps: Math.round((framesInWindow * 1000) / windowElapsed),
        width: currentViewport?.width ?? 0,
        height: currentViewport?.height ?? 0,
        pixelRatio: currentViewport?.pixelRatio ?? 1,
        drawCalls: renderer.info.render.calls,
        triangles: renderer.info.render.triangles,
      });
      framesInWindow = 0;
      statsWindowStart = timestamp;
    }
  }

  return {
    start(): void {
      if (running) {
        return;
      }
      running = true;
      // 先量一次尺寸再起循环：容器可能已经有尺寸，等 ResizeObserver 首次回调太被动
      applyViewport();
      statsWindowStart = performance.now();
      rafId = requestAnimationFrame(frame);
    },

    setAutoRotate(enabled: boolean): void {
      controls.autoRotate = enabled;
    },

    setShadows(enabled: boolean): void {
      renderer.shadowMap.enabled = enabled;
      renderer.shadowMap.needsUpdate = true;
      /**
       * 开关阴影会让所有材质的着色器变体失效，必须显式要求重新编译。
       * 漏掉这一步的表现是"关掉阴影后画面变暗/花屏"，而不是没变化。
       */
      world.scene.traverse((child) => {
        const holder = child as { material?: Material | Material[] };
        if (!holder.material) {
          return;
        }
        const materials = Array.isArray(holder.material) ? holder.material : [holder.material];
        materials.forEach((material) => {
          material.needsUpdate = true;
        });
      });
    },

    setWireframe(enabled: boolean): void {
      world.setWireframe(enabled);
    },

    setGridVisible(visible: boolean): void {
      world.setGridVisible(visible);
    },

    dispose(): void {
      running = false;
      cancelAnimationFrame(rafId);
      observer?.disconnect();
      window.removeEventListener('resize', applyViewport);
      // 断开 Page Visibility 监听：不解开的话每次挂载都会在 document 上多留一个监听器
      timer.dispose();

      controls.dispose();
      environmentTarget.dispose();
      world.scene.environment = null;
      world.dispose();
      disposeRenderer();
      // 画布随上下文一起丢：留着它只会诱使后来者复用一个已经 lost 的 canvas
      canvas.remove();
    },
  };
}
