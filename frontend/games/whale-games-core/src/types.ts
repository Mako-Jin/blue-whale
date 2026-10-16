// 游戏类型定义占位

export interface Entity {
  id: string;
  x: number;
  y: number;
  width: number;
  height: number;
  update(deltaTime: number): void;
  render(ctx: CanvasRenderingContext2D): void;
}

export interface GameState {
  score: number;
  level: number;
  paused: boolean;
}
