// Three.js 场景管理占位 - 后续封装 Scene / Camera / Renderer 的初始化、 resize、dispose 等

export interface SceneManagerOptions {
  container: HTMLElement;
  backgroundColor?: number | string;
  antialias?: boolean;
}

export class WhaleSceneManager {
  // TODO: 封装 three.js 场景初始化
  private readonly options: SceneManagerOptions;

  constructor(options: SceneManagerOptions) {
    this.options = options;
  }

  init(): void {
    const { container, backgroundColor, antialias } = this.options;
    // 占位：new THREE.Scene() + PerspectiveCamera + WebGLRenderer
    void container;
    void backgroundColor;
    void antialias;
  }

  resize(): void {
    // 占位：处理容器 resize
  }

  dispose(): void {
    // 占位：释放资源
  }
}
