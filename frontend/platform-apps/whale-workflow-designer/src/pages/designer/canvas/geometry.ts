// 画布几何：网格 / 缩放常量、坐标吸附、分组包围盒、节点对齐（纯函数）。

import { NODE_SIZE, type CanvasNode } from '../model';

/** 坐标吸附网格（px） */
export const GRID = 10;

/** 对齐吸附阈值（px） */
export const ALIGN_THRESHOLD = 6;

/** 缩放范围与步进 */
export const MIN_ZOOM = 0.4;
export const MAX_ZOOM = 2;
export const ZOOM_STEP = 1.2;

/** 画布最小尺寸与右侧 / 底部留白（内容超出时自动扩展） */
export const CANVAS_MIN_WIDTH = 1200;
export const CANVAS_MIN_HEIGHT = 640;
export const CANVAS_PADDING = 120;

/** 分组容器内边距与顶部标题区高度 */
export const GROUP_PADDING = 24;
export const GROUP_LABEL_HEIGHT = 24;

/** 对齐结果：坐标 + 命中的参考线（画布坐标，无命中为 null） */
export interface Alignment {
  x: number;
  y: number;
  guideX: number | null;
  guideY: number | null;
}

/** 坐标吸附到网格并夹取非负 */
export function snap(value: number): number {
  return Math.max(0, Math.round(value / GRID) * GRID);
}

/** 夹取缩放比例 */
export function clampZoom(value: number): number {
  return Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, value));
}

/**
 * 计算分组容器包围盒（仅统计归属该分组的节点，坐标由节点派生，不持久化）。
 *
 * @param groupKey 分组键
 * @param nodes 全部画布节点
 * @returns 容器矩形；分组下无节点时返回 null
 */
export function groupBounds(
  groupKey: string,
  nodes: CanvasNode[],
): { x: number; y: number; width: number; height: number } | null {
  let minX = Number.POSITIVE_INFINITY;
  let minY = Number.POSITIVE_INFINITY;
  let maxX = Number.NEGATIVE_INFINITY;
  let maxY = Number.NEGATIVE_INFINITY;
  let found = false;
  nodes.forEach((node) => {
    if (node.groupKey !== groupKey) {
      return;
    }
    const size = NODE_SIZE[node.type];
    found = true;
    minX = Math.min(minX, node.x);
    minY = Math.min(minY, node.y);
    maxX = Math.max(maxX, node.x + size.width);
    maxY = Math.max(maxY, node.y + size.height);
  });
  if (!found) {
    return null;
  }
  const x = Math.max(0, minX - GROUP_PADDING);
  const y = Math.max(0, minY - GROUP_PADDING - GROUP_LABEL_HEIGHT);
  return { x, y, width: maxX + GROUP_PADDING - x, height: maxY + GROUP_PADDING - y };
}

/** 取偏差最小的候选（无候选返回 undefined） */
function closest(
  candidates: { delta: number; line: number }[],
): { delta: number; line: number } | undefined {
  return candidates.reduce<{ delta: number; line: number } | undefined>(
    (best, item) => (!best || Math.abs(item.delta) < Math.abs(best.delta) ? item : best),
    undefined,
  );
}

/**
 * 计算节点与其他节点的左/中/右、上/中/下对齐：阈值内吸附并给出参考线坐标。
 *
 * @param node 被拖动节点
 * @param x 候选左上角 x
 * @param y 候选左上角 y
 * @param others 其他节点
 * @returns 吸附后坐标与参考线
 */
export function alignNode(node: CanvasNode, x: number, y: number, others: CanvasNode[]): Alignment {
  const size = NODE_SIZE[node.type];
  const xCandidates: { delta: number; line: number }[] = [];
  const yCandidates: { delta: number; line: number }[] = [];
  const push = (
    list: { delta: number; line: number }[],
    value: number,
    target: number,
    line: number,
  ) => {
    const delta = target - value;
    if (Math.abs(delta) <= ALIGN_THRESHOLD) {
      list.push({ delta, line });
    }
  };
  others.forEach((other) => {
    const otherSize = NODE_SIZE[other.type];
    // 竖向：左-左 / 中-中 / 右-右 / 左-右 / 右-左
    push(xCandidates, x, other.x, other.x);
    push(
      xCandidates,
      x + size.width / 2,
      other.x + otherSize.width / 2,
      other.x + otherSize.width / 2,
    );
    push(xCandidates, x + size.width, other.x + otherSize.width, other.x + otherSize.width);
    push(xCandidates, x, other.x + otherSize.width, other.x + otherSize.width);
    push(xCandidates, x + size.width, other.x, other.x);
    // 横向：上-上 / 中-中 / 下-下 / 上-下 / 下-上
    push(yCandidates, y, other.y, other.y);
    push(
      yCandidates,
      y + size.height / 2,
      other.y + otherSize.height / 2,
      other.y + otherSize.height / 2,
    );
    push(yCandidates, y + size.height, other.y + otherSize.height, other.y + otherSize.height);
    push(yCandidates, y, other.y + otherSize.height, other.y + otherSize.height);
    push(yCandidates, y + size.height, other.y, other.y);
  });
  const bestX = closest(xCandidates);
  const bestY = closest(yCandidates);
  return {
    x: x + (bestX?.delta ?? 0),
    y: y + (bestY?.delta ?? 0),
    guideX: bestX?.line ?? null,
    guideY: bestY?.line ?? null,
  };
}
