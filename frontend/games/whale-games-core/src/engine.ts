// 游戏引擎占位 - 后续实现游戏循环、输入管理、场景管理等

export interface GameConfig {
  fps?: number;
  width?: number;
  height?: number;
}

export class WhaleGameEngine {
  private config: Required<GameConfig>;
  private running = false;

  constructor(config: GameConfig = {}) {
    this.config = {
      fps: 60,
      width: 800,
      height: 600,
      ...config,
    };
  }

  start(): void {
    if (this.running) return;
    const { fps, width, height } = this.config;
    this.running = true;
    // TODO: 游戏主循环 requestAnimationFrame，使用 fps / width / height
    void fps;
    void width;
    void height;
  }

  stop(): void {
    this.running = false;
    // TODO: 清理资源
  }

  isRunning(): boolean {
    return this.running;
  }
}
