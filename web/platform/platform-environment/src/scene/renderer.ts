import { ACESFilmicToneMapping, PCFShadowMap, SRGBColorSpace, WebGLRenderer } from 'three';
import type { Viewport } from './viewport';

/**
 * 渲染器装配。
 *
 * 与 `world.ts` 分开的原因很实际：`WebGLRenderer` 构造时就会去要 WebGL 上下文，
 * 在没有 GPU 的环境（jsdom、CI）里**直接抛错**。把这一步单独隔出来，
 * 场景图的逻辑就都能在无 GPU 环境下测试。
 *
 * **本函数只负责 GPU 资源，不碰 DOM** —— 画布由调用方（`stage.ts`）创建并持有。
 * 调用方必须保证：`dispose()` 之后的画布**不得再次使用**，要重建就换一张新的。
 * 原因见 `stage.ts` 中 `createStage` 的注释。
 */

export type RendererHandle = {
  renderer: WebGLRenderer;
  resize(viewport: Viewport): void;
  dispose(): void;
};

export function createRenderer(canvas: HTMLCanvasElement): RendererHandle {
  const renderer = new WebGLRenderer({
    canvas,
    antialias: true,
    // 画面是持续动画，不需要保留上一帧的绘制缓冲
    preserveDrawingBuffer: false,
    powerPreference: 'high-performance',
  });

  // 颜色空间与色调映射必须显式设：默认值在不同 three 版本间变过，
  // 写字面上比"依赖默认"稳。
  renderer.outputColorSpace = SRGBColorSpace;
  renderer.toneMapping = ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.05;

  /**
   * 阴影贴图类型是**渲染器级别的画质选择**，"开不开阴影"是另一件事 ——
   * 后者由舞台的 `setShadows()` 控制（对应界面上的开关）。
   *
   * 这里写 `PCFShadowMap` 而不是看起来更"高级"的 `PCFSoftShadowMap`：
   * 后者在 three r186 已被移除，写上只会得到一条 `has been removed` 的运行时警告
   * 并静默退回 `PCFShadowMap` —— 看起来生效了，其实什么都没设。**显式写出来的
   * 意义正在于此**：默认值是会变的（PCFSoftShadowMap 自己就当过默认值），
   * 写字面上比依赖默认稳。
   */
  renderer.shadowMap.type = PCFShadowMap;

  return {
    renderer,

    resize(viewport: Viewport): void {
      renderer.setPixelRatio(viewport.pixelRatio);
      /**
       * 第三个参数 `updateStyle` 传 **false**：
       * canvas 的显示尺寸由 CSS 决定（铺满容器），这里只调整绘制缓冲的分辨率。
       * 传 true 的话 three 会往 canvas 上写内联 `style.width/height`，
       * 那会覆盖掉 CSS，容器一变化就打架。
       */
      renderer.setSize(viewport.width, viewport.height, false);
    },

    dispose(): void {
      renderer.dispose();
      /**
       * 主动归还 WebGL 上下文。
       *
       * 这一步在微前端里**不是可选项**：浏览器对同时存在的 WebGL 上下文有数量上限
       * （通常 ~16 个）。子应用被反复挂载/卸载时，只 `dispose()` 的上下文仍会被
       * 浏览器保留一段时间，攒够上限后浏览器会开始回收最旧的上下文 ——
       * 表现是"切换几次之后画面变白，控制台一条错误都没有"。
       */
      renderer.forceContextLoss();
    },
  };
}
