import {
  AmbientLight,
  Color,
  DirectionalLight,
  PerspectiveCamera,
  Scene,
  Texture,
  WebGLRenderer,
  type Material,
} from 'three';

/**
 * 相机配置（缺省 fov=60 / near=0.1 / far=1000 / position=(0,0,5)）
 */
export interface SceneCameraOptions {
  fov?: number;
  near?: number;
  far?: number;
  position?: readonly [number, number, number];
}

/**
 * 场景管理器配置
 */
export interface SceneManagerOptions {
  /** 画布挂载容器（尺寸取自容器的 clientWidth/clientHeight） */
  container: HTMLElement;
  /** 背景色（十六进制数值或 CSS 颜色字符串） */
  backgroundColor?: number | string;
  /** 是否开启 WebGL 抗锯齿，缺省 true */
  antialias?: boolean;
  /** 相机参数覆盖 */
  camera?: SceneCameraOptions;
  /** 设备像素比，缺省 min(devicePixelRatio, 2) */
  pixelRatio?: number;
  /** 是否跟随容器尺寸自动 resize，缺省 true（环境不支持 ResizeObserver 时退化） */
  autoResize?: boolean;
}

/**
 * 帧回调：入参为距上一帧的秒数与累计运行秒数
 */
export type SceneFrameCallback = (delta: number, elapsed: number) => void;

/** 缺省相机参数 */
const DEFAULT_CAMERA = {
  fov: 60,
  near: 0.1,
  far: 1000,
  position: [0, 0, 5] as const,
};

/** 单帧最大时间步长（秒），避免标签页切回时产生跳帧 */
const MAX_FRAME_DELTA = 0.1;

/**
 * three.js 场景管理器：封装 Scene / PerspectiveCamera / WebGLRenderer 的初始化、
 * 自适应 resize、渲染循环、帧回调注册与资源释放。
 *
 * <p>使用方式：{@code new WhaleSceneManager({ container }).init().start()}；
 * 卸载时调用 {@code dispose()}，无需业务侧关心 WebGL 上下文与监听器清理。</p>
 */
export class WhaleSceneManager {
  private readonly options: SceneManagerOptions;

  private readonly frameCallbacks = new Set<SceneFrameCallback>();

  private scene: Scene | null = null;

  private camera: PerspectiveCamera | null = null;

  private renderer: WebGLRenderer | null = null;

  private resizeObserver: ResizeObserver | null = null;

  private animationFrame = 0;

  private running = false;

  private lastTime = 0;

  private elapsed = 0;

  constructor(options: SceneManagerOptions) {
    this.options = options;
  }

  /**
   * 初始化场景、相机与渲染器并挂载画布（重复调用幂等）
   *
   * @returns 当前实例，便于链式调用
   */
  init(): this {
    if (this.renderer) {
      return this;
    }
    const { container } = this.options;
    const scene = new Scene();
    if (this.options.backgroundColor !== undefined) {
      scene.background = new Color(this.options.backgroundColor);
    }
    const cameraOptions = { ...DEFAULT_CAMERA, ...this.options.camera };
    const camera = new PerspectiveCamera(
      cameraOptions.fov,
      1,
      cameraOptions.near,
      cameraOptions.far,
    );
    camera.position.set(...cameraOptions.position);

    const renderer = new WebGLRenderer({ antialias: this.options.antialias ?? true });
    renderer.setPixelRatio(
      this.options.pixelRatio ?? Math.min(globalThis.devicePixelRatio || 1, 2),
    );
    container.appendChild(renderer.domElement);

    scene.add(new AmbientLight(0xffffff, 0.6));
    const directional = new DirectionalLight(0xffffff, 0.8);
    directional.position.set(5, 10, 7.5);
    scene.add(directional);

    this.scene = scene;
    this.camera = camera;
    this.renderer = renderer;

    this.resize();
    if (this.options.autoResize !== false && typeof ResizeObserver !== 'undefined') {
      this.resizeObserver = new ResizeObserver(() => this.resize());
      this.resizeObserver.observe(container);
    }
    this.render();
    return this;
  }

  /**
   * 获取场景对象
   *
   * @returns three.js 场景
   * @throws Error 尚未调用 {@link init()} 时抛出
   */
  getScene(): Scene {
    this.assertInitialized();
    return this.scene as Scene;
  }

  /**
   * 获取透视相机
   *
   * @returns three.js 透视相机
   * @throws Error 尚未调用 {@link init()} 时抛出
   */
  getCamera(): PerspectiveCamera {
    this.assertInitialized();
    return this.camera as PerspectiveCamera;
  }

  /**
   * 获取 WebGL 渲染器
   *
   * @returns three.js WebGL 渲染器
   * @throws Error 尚未调用 {@link init()} 时抛出
   */
  getRenderer(): WebGLRenderer {
    this.assertInitialized();
    return this.renderer as WebGLRenderer;
  }

  /**
   * 按容器当前尺寸更新相机纵横比与渲染器尺寸（容器尺寸取 0 时按 1 处理，避免除零）
   */
  resize(): void {
    if (!this.renderer || !this.camera) {
      return;
    }
    const width = Math.max(1, this.options.container.clientWidth);
    const height = Math.max(1, this.options.container.clientHeight);
    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(width, height);
  }

  /**
   * 渲染一帧
   */
  render(): void {
    if (this.scene && this.camera && this.renderer) {
      this.renderer.render(this.scene, this.camera);
    }
  }

  /**
   * 注册帧回调（每帧渲染前触发）
   *
   * @param callback 帧回调
   * @returns 取消注册函数
   */
  onFrame(callback: SceneFrameCallback): () => void {
    this.frameCallbacks.add(callback);
    return () => {
      this.frameCallbacks.delete(callback);
    };
  }

  /**
   * 启动渲染循环（重复调用幂等）
   */
  start(): void {
    if (this.running) {
      return;
    }
    this.assertInitialized();
    this.running = true;
    this.lastTime = performance.now();
    const tick = (now: number) => {
      if (!this.running) {
        return;
      }
      const delta = Math.min((now - this.lastTime) / 1000, MAX_FRAME_DELTA);
      this.lastTime = now;
      this.elapsed += delta;
      this.frameCallbacks.forEach((callback) => callback(delta, this.elapsed));
      this.render();
      this.animationFrame = requestAnimationFrame(tick);
    };
    this.animationFrame = requestAnimationFrame(tick);
  }

  /**
   * 停止渲染循环（已注册的帧回调保留，重新 {@link start()} 后继续生效）
   */
  stop(): void {
    this.running = false;
    if (this.animationFrame) {
      cancelAnimationFrame(this.animationFrame);
      this.animationFrame = 0;
    }
  }

  /**
   * 释放资源：停止循环、解绑 resize 监听、销毁场景内的几何体/材质/贴图与 WebGL 上下文、移除画布
   */
  dispose(): void {
    this.stop();
    this.resizeObserver?.disconnect();
    this.resizeObserver = null;
    this.frameCallbacks.clear();
    if (this.scene) {
      WhaleSceneManager.disposeSceneResources(this.scene);
    }
    if (this.renderer) {
      this.renderer.dispose();
      this.renderer.domElement.remove();
    }
    this.scene = null;
    this.camera = null;
    this.renderer = null;
    this.elapsed = 0;
  }

  /**
   * 断言已初始化
   *
   * @throws Error 尚未调用 {@link init()} 时抛出
   */
  private assertInitialized(): void {
    if (!this.scene || !this.camera || !this.renderer) {
      throw new Error('WhaleSceneManager 尚未 init()，请先调用 init()');
    }
  }

  /**
   * 递归释放场景内几何体、材质与材质引用的贴图
   *
   * @param scene 待释放场景
   */
  private static disposeSceneResources(scene: Scene): void {
    scene.traverse((object) => {
      const renderable = object as unknown as {
        geometry?: { dispose: () => void };
        material?: Material | Material[];
      };
      renderable.geometry?.dispose();
      const materials = Array.isArray(renderable.material)
        ? renderable.material
        : renderable.material
          ? [renderable.material]
          : [];
      for (const material of materials) {
        for (const value of Object.values(material as unknown as Record<string, unknown>)) {
          if (value instanceof Texture) {
            value.dispose();
          }
        }
        material.dispose();
      }
    });
    scene.clear();
  }
}
