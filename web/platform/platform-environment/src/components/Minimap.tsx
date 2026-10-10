import { useEffect, useRef } from 'react';
import { arrowPolygon, createMinimapProjection, viewWedgePolygon } from '@/scene';
import type { SceneInteractionSnapshot } from '@/scene';
import './Minimap.less';

/**
 * 左上角小地图。
 *
 * 三个刻意的决定：
 *
 * 1. **自绘 canvas，不走 React 状态。** 小地图每秒要重画 60 次，
 *    用 state 驱动会让整棵组件树跟着重渲染 —— 那是拿最贵的手段做最便宜的事。
 *    这里用 `requestAnimationFrame` 直接画，React 只负责挂载与卸载。
 * 2. **正北朝上，地图不随镜头旋转。** 因此指北针是一个**静态**标记，
 *    永远在正上方 —— 旋转地图时"北"会在边框上跑来跑去，反而更难读。
 *    人物朝向与相机视野各自画成箭头和扇形，两者都朝向哪儿一眼可见。
 * 3. **没有交互场景就整块隐藏**，而不是留一个空框让人以为是坏了。
 */

/** 画布边长（CSS 像素） */
const SIZE = 176;
/** 相机视野扇形的半角；半径跟着相机距离走，见 `viewRadiusWorld` */
const VIEW_HALF_ANGLE = (30 * Math.PI) / 180;
/** 扇形半径 = 相机距离 × 该系数，并夹在 [MIN, MAX] 之间（世界单位） */
const VIEW_RADIUS_FACTOR = 0.95;
const VIEW_RADIUS_MIN = 10;
const VIEW_RADIUS_MAX = 36;
/** 铺装网格间隔（世界单位），与小地图上的另一套网格无关 */
const GRID_STEP = 10;
const CORNER_RADIUS = 10;

/**
 * 视野扇形的世界半径。
 *
 * 让半径随滚轮缩放变化，而不是取个常数：拉远镜头时视野本该更远，
 * 扇形跟着变长，"缩放生效了"这件事在小地图上就是可见的 ——
 * 否则玩家只能凭三维画面的感觉猜。
 */
function viewRadiusWorld(cameraDistance: number): number {
  return Math.min(VIEW_RADIUS_MAX, Math.max(VIEW_RADIUS_MIN, cameraDistance * VIEW_RADIUS_FACTOR));
}

const COLOR = {
  board: 'rgba(15, 20, 32, 0.86)',
  ground: 'rgba(126, 134, 156, 0.18)',
  grid: 'rgba(255, 255, 255, 0.07)',
  groundEdge: 'rgba(255, 255, 255, 0.22)',
  obstacle: 'rgba(185, 154, 94, 0.5)',
  obstacleEdge: 'rgba(255, 214, 138, 0.85)',
  wedge: 'rgba(214, 226, 255, 0.18)',
  player: '#ffd68a',
  playerEdge: 'rgba(24, 18, 8, 0.85)',
  destination: '#7fd4a0',
  frame: 'rgba(255, 255, 255, 0.18)',
} as const;

function roundedRect(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  radius: number,
): void {
  ctx.beginPath();
  ctx.moveTo(x + radius, y);
  ctx.lineTo(x + width - radius, y);
  ctx.quadraticCurveTo(x + width, y, x + width, y + radius);
  ctx.lineTo(x + width, y + height - radius);
  ctx.quadraticCurveTo(x + width, y + height, x + width - radius, y + height);
  ctx.lineTo(x + radius, y + height);
  ctx.quadraticCurveTo(x, y + height, x, y + height - radius);
  ctx.lineTo(x, y + radius);
  ctx.quadraticCurveTo(x, y, x + radius, y);
  ctx.closePath();
}

function tracePolygon(ctx: CanvasRenderingContext2D, points: readonly { x: number; y: number }[]) {
  ctx.beginPath();
  points.forEach((point, index) => {
    if (index === 0) {
      ctx.moveTo(point.x, point.y);
    } else {
      ctx.lineTo(point.x, point.y);
    }
  });
  ctx.closePath();
}

/**
 * 把一张快照画到 canvas 上。
 *
 * 刻意**不导出**：它只在组件的 rAF 里被调用。导出会让这个文件
 * 同时成为"组件文件"和"工具模块"，破坏 Fast Refresh 的组件边界
 * （react-refresh 会就此报警告）。小地图里真正值得单测的几何
 * （投影、方向、扇形、箭头）都在 `scene/minimap.ts` 里，已经是纯函数。
 */
function drawMinimap(ctx: CanvasRenderingContext2D, snapshot: SceneInteractionSnapshot): void {
  const { map, player, cameraAzimuth, destination } = snapshot;
  const projection = createMinimapProjection(SIZE, map.groundSize);
  const half = map.groundSize / 2;
  const center = SIZE / 2;

  ctx.clearRect(0, 0, SIZE, SIZE);

  // ---- 裁剪区内的图层 ----
  ctx.save();
  roundedRect(ctx, 0.5, 0.5, SIZE - 1, SIZE - 1, CORNER_RADIUS);
  ctx.clip();

  ctx.fillStyle = COLOR.board;
  ctx.fillRect(0, 0, SIZE, SIZE);

  const groundTopLeft = projection.toMap(-half, -half);
  const groundSide = projection.toPixels(map.groundSize);

  ctx.fillStyle = COLOR.ground;
  ctx.fillRect(groundTopLeft.x, groundTopLeft.y, groundSide, groundSide);

  ctx.strokeStyle = COLOR.grid;
  ctx.lineWidth = 1;
  for (let offset = -half + GRID_STEP; offset < half; offset += GRID_STEP) {
    const verticalStart = projection.toMap(offset, -half);
    const verticalEnd = projection.toMap(offset, half);
    ctx.beginPath();
    ctx.moveTo(verticalStart.x, verticalStart.y);
    ctx.lineTo(verticalEnd.x, verticalEnd.y);
    ctx.stroke();

    const horizontalStart = projection.toMap(-half, offset);
    const horizontalEnd = projection.toMap(half, offset);
    ctx.beginPath();
    ctx.moveTo(horizontalStart.x, horizontalStart.y);
    ctx.lineTo(horizontalEnd.x, horizontalEnd.y);
    ctx.stroke();
  }

  ctx.strokeStyle = COLOR.groundEdge;
  ctx.strokeRect(groundTopLeft.x, groundTopLeft.y, groundSide, groundSide);

  map.obstacles.forEach((obstacle) => {
    const topLeft = projection.toMap(
      obstacle.x - obstacle.width / 2,
      obstacle.z - obstacle.depth / 2,
    );
    const width = projection.toPixels(obstacle.width);
    const depth = projection.toPixels(obstacle.depth);
    ctx.fillStyle = COLOR.obstacle;
    ctx.fillRect(topLeft.x, topLeft.y, width, depth);
    ctx.strokeStyle = COLOR.obstacleEdge;
    ctx.lineWidth = 1;
    ctx.strokeRect(topLeft.x, topLeft.y, width, depth);
  });

  const playerPoint = projection.toMap(player.x, player.z);

  // 相机视野扇形：镜头正对着哪片地面，这里就画向哪边
  const wedge = viewWedgePolygon(
    playerPoint,
    cameraAzimuth,
    VIEW_HALF_ANGLE,
    projection.toPixels(viewRadiusWorld(snapshot.cameraDistance)),
  );
  tracePolygon(ctx, wedge);
  ctx.fillStyle = COLOR.wedge;
  ctx.fill();

  if (destination) {
    const target = projection.toMap(destination.x, destination.z);
    ctx.strokeStyle = COLOR.destination;
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.arc(target.x, target.y, 4.5, 0, Math.PI * 2);
    ctx.stroke();
    ctx.fillStyle = COLOR.destination;
    ctx.beginPath();
    ctx.arc(target.x, target.y, 1.6, 0, Math.PI * 2);
    ctx.fill();
  }

  // 人物：箭头指向**人物朝向**，与视野扇形（镜头朝向）是两个独立信息
  const arrow = arrowPolygon(playerPoint, player.heading, 9);
  tracePolygon(ctx, arrow);
  ctx.fillStyle = COLOR.player;
  ctx.fill();
  ctx.strokeStyle = COLOR.playerEdge;
  ctx.lineWidth = 1;
  ctx.stroke();

  ctx.restore();

  // ---- 边框（画在裁剪之外，压在圆角上）----
  roundedRect(ctx, 0.5, 0.5, SIZE - 1, SIZE - 1, CORNER_RADIUS);
  ctx.strokeStyle = COLOR.frame;
  ctx.lineWidth = 1;
  ctx.stroke();

  // 四向刻度：正北朝上，因此刻度是固定的
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.35)';
  ctx.lineWidth = 2;
  const ticks: Array<[number, number, number, number]> = [
    [center, 1.5, center, 7],
    [center, SIZE - 1.5, center, SIZE - 7],
    [1.5, center, 7, center],
    [SIZE - 1.5, center, SIZE - 7, center],
  ];
  ticks.forEach(([x1, y1, x2, y2]) => {
    ctx.beginPath();
    ctx.moveTo(x1, y1);
    ctx.lineTo(x2, y2);
    ctx.stroke();
  });
}

type MinimapProps = {
  /**
   * 取当前交互快照。**必须是稳定的读取器** ——
   * 组件每帧调它一次，而它读的是舞台的即时状态，不触发 React 更新。
   */
  getSnapshot: () => SceneInteractionSnapshot | null;
};

export default function Minimap({ getSnapshot }: MinimapProps) {
  const hostRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const snapshotRef = useRef(getSnapshot);

  useEffect(() => {
    snapshotRef.current = getSnapshot;
  });

  useEffect(() => {
    const host = hostRef.current;
    const canvas = canvasRef.current;
    if (!host || !canvas) {
      return;
    }
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      // 拿不到 2D 上下文（极端受限环境）：隐藏整块，不留一个空框
      host.hidden = true;
      return;
    }

    // 高分屏按设备像素比放大绘制缓冲，否则小地图上的文字与线条会糊
    const ratio = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = Math.round(SIZE * ratio);
    canvas.height = Math.round(SIZE * ratio);
    canvas.style.width = `${SIZE}px`;
    canvas.style.height = `${SIZE}px`;

    let frame = 0;
    const render = (): void => {
      frame = requestAnimationFrame(render);
      const snapshot = snapshotRef.current();
      /**
       * 直接改 DOM 的 `hidden`，不走 state。
       *
       * 这是每帧都要做的判断，走 state 就等于每帧问 React 要不要重渲染。
       * `hidden` 不在 JSX 里，React 不会来动它。
       */
      host.hidden = snapshot === null;
      if (!snapshot) {
        return;
      }
      ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
      drawMinimap(ctx, snapshot);
    };
    frame = requestAnimationFrame(render);

    return () => {
      cancelAnimationFrame(frame);
    };
  }, []);

  return (
    <div className="minimap" ref={hostRef}>
      <div className="minimap__frame">
        <canvas className="minimap__canvas" ref={canvasRef} aria-hidden="true" />
        <span className="minimap__compass" title="正北方向（地图上方）">
          <svg viewBox="0 0 12 12" aria-hidden="true">
            <path d="M6 1 L10 10 L6 7.6 L2 10 Z" />
          </svg>
          N
        </span>
      </div>
      <p className="minimap__legend">
        <span>WASD / ↑↓←→ 移动</span>
        <span>右键设置终点</span>
        <span>滚轮缩放</span>
      </p>
    </div>
  );
}
