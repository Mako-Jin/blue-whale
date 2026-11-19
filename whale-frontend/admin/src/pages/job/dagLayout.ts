// DAG 拓扑图布局：按最长路径分层 + 同层按输入顺序排列，输出 SVG 坐标与边路径。
// 纯函数，不依赖 DOM，便于单测；环上节点用迭代松弛封顶，数据异常时也不会死循环。

import type { JobGraphEdgeVO, JobGraphNodeVO } from '../../api/job';

export interface DagLayoutOptions {
  nodeWidth?: number;
  nodeHeight?: number;
  /** 同层节点水平间距 */
  gapX?: number;
  /** 层间垂直间距 */
  gapY?: number;
  /** 画布内边距 */
  padding?: number;
}

export interface DagNodeBox {
  node: JobGraphNodeVO;
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface DagEdgePath {
  from: string;
  to: string;
  path: string;
}

export interface DagLayoutResult {
  nodes: DagNodeBox[];
  edges: DagEdgePath[];
  width: number;
  height: number;
  /** 层数，便于调用方判断是否单层空图 */
  layers: number;
}

const DEFAULTS = {
  nodeWidth: 180,
  nodeHeight: 52,
  gapX: 48,
  gapY: 64,
  padding: 24,
};

/** 计算每个节点的层号：从入边来源逐轮松弛，环上节点最多被推到 n 层后停止 */
function computeLayers(nodes: JobGraphNodeVO[], edges: JobGraphEdgeVO[]): Map<string, number> {
  const layerOf = new Map<string, number>();
  nodes.forEach((node) => layerOf.set(node.id, 0));

  const limit = nodes.length;
  for (let round = 0; round < limit; round += 1) {
    let changed = false;
    edges.forEach((edge) => {
      const fromLayer = layerOf.get(edge.from);
      if (fromLayer === undefined) {
        return;
      }
      const toLayer = layerOf.get(edge.to);
      if (toLayer === undefined) {
        return;
      }
      const candidate = fromLayer + 1;
      // 封顶避免 A→B→A 这类异常数据让层号无限增长
      if (candidate > toLayer && candidate < limit) {
        layerOf.set(edge.to, candidate);
        changed = true;
      }
    });
    if (!changed) {
      break;
    }
  }
  return layerOf;
}

/**
 * 生成拓扑图布局。
 *
 * - 节点按层从上到下排列，层内保持接口返回顺序（后端已按 ID 升序）；
 * - 每层水平居中，避免长链一侧完全贴边；
 * - 边从上游节点右侧连到下游节点左侧，用三次贝塞尔平滑；
 *   下游在上游之前（正常情况下不应出现）时简化为反向曲线，仅作异常数据兜底。
 */
export function layoutDag(
  nodes: JobGraphNodeVO[],
  edges: JobGraphEdgeVO[],
  options: DagLayoutOptions = {},
): DagLayoutResult {
  const { nodeWidth, nodeHeight, gapX, gapY, padding } = { ...DEFAULTS, ...options };
  if (nodes.length === 0) {
    return { nodes: [], edges: [], width: padding * 2, height: padding * 2, layers: 0 };
  }

  const layerOf = computeLayers(nodes, edges);
  const layers: JobGraphNodeVO[][] = [];
  nodes.forEach((node) => {
    const layer = layerOf.get(node.id) ?? 0;
    if (!layers[layer]) {
      layers[layer] = [];
    }
    layers[layer].push(node);
  });

  const layerCount = layers.length;
  const widest = layers.reduce((max, layer) => Math.max(max, layer?.length ?? 0), 0);
  const contentWidth = widest * nodeWidth + Math.max(0, widest - 1) * gapX;
  const contentHeight = layerCount * nodeHeight + Math.max(0, layerCount - 1) * gapY;

  const boxes: DagNodeBox[] = [];
  const boxById = new Map<string, DagNodeBox>();
  layers.forEach((layer, layerIndex) => {
    if (!layer || layer.length === 0) {
      return;
    }
    const rowWidth = layer.length * nodeWidth + (layer.length - 1) * gapX;
    const offsetX = padding + (contentWidth - rowWidth) / 2;
    const y = padding + layerIndex * (nodeHeight + gapY);
    layer.forEach((node, index) => {
      const box: DagNodeBox = {
        node,
        x: offsetX + index * (nodeWidth + gapX),
        y,
        width: nodeWidth,
        height: nodeHeight,
      };
      boxes.push(box);
      boxById.set(node.id, box);
    });
  });

  const paths: DagEdgePath[] = edges
    .map((edge) => {
      const from = boxById.get(edge.from);
      const to = boxById.get(edge.to);
      if (!from || !to) {
        return null;
      }
      const startX = from.x + from.width;
      const startY = from.y + from.height / 2;
      const endX = to.x;
      const endY = to.y + to.height / 2;
      // 横向控制点随层间距与节点宽度自适应，短边不至于过度弯曲
      const control = Math.max(40, Math.abs(endX - startX) / 2);
      return {
        from: edge.from,
        to: edge.to,
        path: `M ${startX} ${startY} C ${startX + control} ${startY}, ${endX - control} ${endY}, ${endX} ${endY}`,
      };
    })
    .filter((path): path is DagEdgePath => path !== null);

  return {
    nodes: boxes,
    edges: paths,
    width: contentWidth + padding * 2,
    height: contentHeight + padding * 2,
    layers: layerCount,
  };
}
