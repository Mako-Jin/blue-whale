// 自由画布（SVG 自绘 + @dnd-kit 拖入 / 拖动）：
// - 整块画布为 droppable：从 Palette 拖入落点按 pointer 换算坐标并新建节点；
// - 节点为可拖拽源（拖动结束写回 x/y，10px 网格吸附 + 6px 对齐吸附并显示参考线）；
// - 连线（EdgePath）在下层，节点在上层；选中态高亮、校验 errorKeys 标红；
// - 缩放（工具栏 / Ctrl+滚轮，0.4x~2x）与平移（空格+拖动 / 中键拖动）。

import {
  DndContext,
  DragOverlay,
  PointerSensor,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragMoveEvent,
  type DragStartEvent,
} from '@dnd-kit/core';
import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
} from 'react';
import type { NodeType } from '../../api/workflow';
import EdgePath from './EdgePath';
import NodeShape from './NodeShape';
import {
  CANVAS_DROPPABLE_ID,
  NODE_SIZE,
  NODE_TYPE_COLORS,
  NODE_TYPE_LABELS,
  type CanvasModel,
  type CanvasNode,
  type Selection,
} from './model';

/** 节点拖拽源 id 前缀 */
const NODE_DRAG_PREFIX = 'wf-node-';

/** 坐标吸附网格（px） */
const GRID = 10;

/** 对齐吸附阈值（px） */
const ALIGN_THRESHOLD = 6;

/** 缩放范围与步进 */
const MIN_ZOOM = 0.4;
const MAX_ZOOM = 2;
const ZOOM_STEP = 1.2;

/** 画布最小尺寸与右侧 / 底部留白（内容超出时自动扩展） */
const CANVAS_MIN_WIDTH = 1200;
const CANVAS_MIN_HEIGHT = 640;
const CANVAS_PADDING = 120;

/** 分组容器内边距与顶部标题区高度 */
const GROUP_PADDING = 24;
const GROUP_LABEL_HEIGHT = 24;

/** 拖拽源 data 结构（Palette 与画布节点共用判别字段 source） */
interface DraggableData {
  source?: 'palette' | 'node';
  nodeType?: NodeType;
  key?: string;
}

/** 对齐结果：坐标 + 命中的参考线（画布坐标，无命中为 null） */
interface Alignment {
  x: number;
  y: number;
  guideX: number | null;
  guideY: number | null;
}

/** 坐标吸附到网格并夹取非负 */
function snap(value: number): number {
  return Math.max(0, Math.round(value / GRID) * GRID);
}

/** 夹取缩放比例 */
function clampZoom(value: number): number {
  return Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, value));
}

/**
 * 计算分组容器包围盒（仅统计归属该分组的节点，坐标由节点派生，不持久化）。
 *
 * @param groupKey 分组键
 * @param nodes 全部画布节点
 * @returns 容器矩形；分组下无节点时返回 null
 */
function groupBounds(
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
function closest(candidates: { delta: number; line: number }[]): { delta: number; line: number } | undefined {
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
function alignNode(node: CanvasNode, x: number, y: number, others: CanvasNode[]): Alignment {
  const size = NODE_SIZE[node.type];
  const xCandidates: { delta: number; line: number }[] = [];
  const yCandidates: { delta: number; line: number }[] = [];
  const push = (list: { delta: number; line: number }[], value: number, target: number, line: number) => {
    const delta = target - value;
    if (Math.abs(delta) <= ALIGN_THRESHOLD) {
      list.push({ delta, line });
    }
  };
  others.forEach((other) => {
    const otherSize = NODE_SIZE[other.type];
    // 竖向：左-左 / 中-中 / 右-右 / 左-右 / 右-左
    push(xCandidates, x, other.x, other.x);
    push(xCandidates, x + size.width / 2, other.x + otherSize.width / 2, other.x + otherSize.width / 2);
    push(xCandidates, x + size.width, other.x + otherSize.width, other.x + otherSize.width);
    push(xCandidates, x, other.x + otherSize.width, other.x + otherSize.width);
    push(xCandidates, x + size.width, other.x, other.x);
    // 横向：上-上 / 中-中 / 下-下 / 上-下 / 下-上
    push(yCandidates, y, other.y, other.y);
    push(yCandidates, y + size.height / 2, other.y + otherSize.height / 2, other.y + otherSize.height / 2);
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

/** 可拖拽画布节点（平移写回坐标由父层 onMoveNode 处理） */
function DraggableNode({
  node,
  selected,
  error,
  onSelect,
}: {
  node: CanvasNode;
  selected: boolean;
  error: boolean;
  onSelect: (key: string) => void;
}) {
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({
    id: `${NODE_DRAG_PREFIX}${node.key}`,
    data: { source: 'node', key: node.key },
  });
  const offsetX = transform ? transform.x : 0;
  const offsetY = transform ? transform.y : 0;
  // dnd-kit 的 setNodeRef 按 HTMLElement 声明，SVG <g> 需转型
  const nodeRef = setNodeRef as unknown as (instance: SVGGElement | null) => void;

  return (
    <g
      ref={nodeRef}
      transform={`translate(${node.x + offsetX}, ${node.y + offsetY})`}
      style={{ cursor: isDragging ? 'grabbing' : 'grab', opacity: isDragging ? 0.85 : 1 }}
      onClick={(event) => {
        event.stopPropagation();
        onSelect(node.key);
      }}
      {...listeners}
      {...attributes}
    >
      <NodeShape node={node} selected={selected} error={error} />
    </g>
  );
}

interface CanvasProps {
  /** 左侧调色板（须与画布处于同一 DndContext 内，故由 Canvas 渲染） */
  palette: ReactNode;
  model: CanvasModel;
  selected: Selection | null;
  /** 校验 error 的 refKey 集合（节点 key 或流转 key） */
  errorKeys: Set<string>;
  /** 连线起点节点 key（非空时点击目标节点即建流） */
  linkFrom: string | null;
  onSelectNode: (key: string) => void;
  onSelectFlow: (key: string) => void;
  onClearSelection: () => void;
  onMoveNode: (key: string, x: number, y: number) => void;
  onDropNode: (type: NodeType, x: number, y: number) => void;
}

export default function Canvas({
  palette,
  model,
  selected,
  errorKeys,
  linkFrom,
  onSelectNode,
  onSelectFlow,
  onClearSelection,
  onMoveNode,
  onDropNode,
}: CanvasProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const { setNodeRef, isOver } = useDroppable({ id: CANVAS_DROPPABLE_ID });
  const [activeType, setActiveType] = useState<NodeType | null>(null);
  const [zoom, setZoom] = useState(1);
  const [guide, setGuide] = useState<{ x: number | null; y: number | null }>({ x: null, y: null });
  const panRef = useRef<{ x: number; y: number; left: number; top: number } | null>(null);
  const spaceRef = useRef(false);
  // distance 4：区分「点击选中」与「拖拽」
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 4 } }));

  const nodesByKey = useMemo(
    () => new Map(model.nodes.map((node) => [node.key, node])),
    [model.nodes],
  );

  const { width, height } = useMemo(() => {
    let maxX = CANVAS_MIN_WIDTH - CANVAS_PADDING;
    let maxY = CANVAS_MIN_HEIGHT - CANVAS_PADDING;
    model.nodes.forEach((node) => {
      const size = NODE_SIZE[node.type];
      maxX = Math.max(maxX, node.x + size.width);
      maxY = Math.max(maxY, node.y + size.height);
    });
    return { width: maxX + CANVAS_PADDING, height: maxY + CANVAS_PADDING };
  }, [model.nodes]);

  // Ctrl / Cmd + 滚轮：以指针位置为中心缩放（原生监听以允许 preventDefault）
  useEffect(() => {
    const container = containerRef.current;
    if (!container) {
      return;
    }
    const onWheel = (event: WheelEvent) => {
      if (!event.ctrlKey && !event.metaKey) {
        return;
      }
      event.preventDefault();
      const rect = container.getBoundingClientRect();
      const pointerX = event.clientX - rect.left + container.scrollLeft;
      const pointerY = event.clientY - rect.top + container.scrollTop;
      const next = clampZoom(zoom * (event.deltaY < 0 ? ZOOM_STEP : 1 / ZOOM_STEP));
      if (next !== zoom) {
        container.scrollLeft = (pointerX * next) / zoom - (event.clientX - rect.left);
        container.scrollTop = (pointerY * next) / zoom - (event.clientY - rect.top);
        setZoom(next);
      }
    };
    container.addEventListener('wheel', onWheel, { passive: false });
    return () => container.removeEventListener('wheel', onWheel);
  }, [zoom]);

  // 空格按下即进入「抓手平移」模式（输入框内不劫持）
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      const tag = target?.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA' || target?.isContentEditable) {
        return;
      }
      if (event.code === 'Space') {
        spaceRef.current = true;
      }
    };
    const onKeyUp = (event: KeyboardEvent) => {
      if (event.code === 'Space') {
        spaceRef.current = false;
      }
    };
    window.addEventListener('keydown', onKeyDown);
    window.addEventListener('keyup', onKeyUp);
    return () => {
      window.removeEventListener('keydown', onKeyDown);
      window.removeEventListener('keyup', onKeyUp);
    };
  }, []);

  const attachRef = (element: HTMLDivElement | null) => {
    containerRef.current = element;
    setNodeRef(element);
  };

  /** 平移：中键或空格 + 左键拖动 */
  const handlePointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    const container = containerRef.current;
    const panning = event.button === 1 || (event.button === 0 && spaceRef.current);
    if (!container || !panning) {
      return;
    }
    event.preventDefault();
    panRef.current = {
      x: event.clientX,
      y: event.clientY,
      left: container.scrollLeft,
      top: container.scrollTop,
    };
    container.setPointerCapture(event.pointerId);
  };

  const handlePointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    const container = containerRef.current;
    const start = panRef.current;
    if (!container || !start) {
      return;
    }
    container.scrollLeft = start.left - (event.clientX - start.x);
    container.scrollTop = start.top - (event.clientY - start.y);
  };

  const endPan = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (!panRef.current) {
      return;
    }
    panRef.current = null;
    containerRef.current?.releasePointerCapture(event.pointerId);
  };

  /** 以缩放比例换算拖拽位移（屏幕 px → 画布坐标） */
  const dragTarget = (node: CanvasNode, deltaX: number, deltaY: number): Alignment => {
    const others = model.nodes.filter((item) => item.key !== node.key);
    return alignNode(node, node.x + deltaX / zoom, node.y + deltaY / zoom, others);
  };

  const handleDragStart = (event: DragStartEvent) => {
    const data = event.active.data.current as DraggableData | undefined;
    setActiveType(data?.source === 'palette' ? data.nodeType ?? null : null);
  };

  const handleDragMove = (event: DragMoveEvent) => {
    const data = event.active.data.current as DraggableData | undefined;
    if (data?.source !== 'node' || !data.key) {
      return;
    }
    const node = nodesByKey.get(data.key);
    if (!node) {
      return;
    }
    const target = dragTarget(node, event.delta.x, event.delta.y);
    setGuide({ x: target.guideX, y: target.guideY });
  };

  const handleDragEnd = (event: DragEndEvent) => {
    setActiveType(null);
    setGuide({ x: null, y: null });
    const data = event.active.data.current as DraggableData | undefined;

    if (data?.source === 'node' && data.key) {
      const node = nodesByKey.get(data.key);
      if (!node) {
        return;
      }
      const target = dragTarget(node, event.delta.x, event.delta.y);
      // 命中参考线的轴按对齐值落位，另一轴按网格吸附
      const x = target.guideX === null ? snap(target.x) : Math.round(target.x);
      const y = target.guideY === null ? snap(target.y) : Math.round(target.y);
      onMoveNode(node.key, x, y);
      return;
    }

    if (data?.source === 'palette' && data.nodeType) {
      const translated = event.active.rect.current.translated;
      const container = containerRef.current;
      if (!translated || !container) {
        return;
      }
      const rect = container.getBoundingClientRect();
      // 落点 = 拖拽拖影中心（客户端坐标）→ 画布局部坐标（按缩放还原）
      const centerX = (translated.left + translated.width / 2 - rect.left + container.scrollLeft) / zoom;
      const centerY = (translated.top + translated.height / 2 - rect.top + container.scrollTop) / zoom;
      const size = NODE_SIZE[data.nodeType];
      onDropNode(data.nodeType, snap(centerX - size.width / 2), snap(centerY - size.height / 2));
    }
  };

  /** 缩放到指定值（保持滚动位置近似不变） */
  const applyZoom = (next: number) => {
    setZoom(clampZoom(next));
  };

  /** 适应画布：按容器可视区与内容尺寸取最小值（不超过 1x） */
  const fitCanvas = () => {
    const container = containerRef.current;
    if (!container) {
      return;
    }
    const scale = Math.min(container.clientWidth / width, container.clientHeight / height, 1);
    setZoom(clampZoom(Math.floor(scale * 100) / 100));
    container.scrollLeft = 0;
    container.scrollTop = 0;
  };

  return (
    <DndContext
      sensors={sensors}
      onDragStart={handleDragStart}
      onDragMove={handleDragMove}
      onDragEnd={handleDragEnd}
      onDragCancel={() => {
        setActiveType(null);
        setGuide({ x: null, y: null });
      }}
    >
      <div style={{ display: 'flex', gap: 12, height: '100%' }}>
        <div
          style={{
            flex: '0 0 208px',
            width: 208,
            overflow: 'auto',
            paddingRight: 8,
            borderRight: '1px solid #f0f0f0',
          }}
        >
          {palette}
        </div>
        <div
          ref={attachRef}
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={endPan}
          onPointerCancel={endPan}
          style={{
            position: 'relative',
            flex: 1,
            minWidth: 0,
            overflow: 'auto',
            border: `1px dashed ${isOver ? '#1677ff' : '#d9d9d9'}`,
            borderRadius: 6,
            background: '#fafafa',
            cursor: panRef.current ? 'grabbing' : 'default',
          }}
        >
        <svg
          width={width * zoom}
          height={height * zoom}
          viewBox={`0 0 ${width} ${height}`}
          style={{ display: 'block' }}
          onClick={(event) => {
            if (event.target === event.currentTarget) {
              onClearSelection();
            }
          }}
        >
          <defs>
            <marker id="wf-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
              <path d="M 0 0 L 10 5 L 0 10 z" fill="#8c8c8c" />
            </marker>
            <marker id="wf-arrow-selected" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
              <path d="M 0 0 L 10 5 L 0 10 z" fill="#1677ff" />
            </marker>
            <marker id="wf-arrow-error" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
              <path d="M 0 0 L 10 5 L 0 10 z" fill="#ff4d4f" />
            </marker>
          </defs>

          {guide.x !== null ? (
            <line
              x1={guide.x}
              y1={0}
              x2={guide.x}
              y2={height}
              stroke="#1677ff"
              strokeWidth={1}
              strokeDasharray="4 3"
              pointerEvents="none"
            />
          ) : null}
          {guide.y !== null ? (
            <line
              x1={0}
              y1={guide.y}
              x2={width}
              y2={guide.y}
              stroke="#1677ff"
              strokeWidth={1}
              strokeDasharray="4 3"
              pointerEvents="none"
            />
          ) : null}

          <g>
            {model.groups.map((group) => {
              const bounds = groupBounds(group.key, model.nodes);
              if (!bounds) {
                return null;
              }
              return (
                <g key={group.key} pointerEvents="none">
                  <rect
                    x={bounds.x}
                    y={bounds.y}
                    width={bounds.width}
                    height={bounds.height}
                    rx={8}
                    fill="rgba(22,119,255,0.04)"
                    stroke="#91caff"
                    strokeDasharray="6 4"
                  />
                  <text x={bounds.x + 10} y={bounds.y + 16} fontSize={12} fill="#1677ff">
                    {group.name || group.key}
                  </text>
                </g>
              );
            })}
          </g>

          <g>
            {model.flows.map((flow) => {
              const from = nodesByKey.get(flow.from);
              const to = nodesByKey.get(flow.to);
              if (!from || !to) {
                return null;
              }
              return (
                <EdgePath
                  key={flow.key}
                  flow={flow}
                  from={from}
                  to={to}
                  selected={selected?.kind === 'flow' && selected.key === flow.key}
                  error={errorKeys.has(flow.key)}
                  onSelect={onSelectFlow}
                />
              );
            })}
          </g>

          <g>
            {model.nodes.map((node) => (
              <DraggableNode
                key={node.key}
                node={node}
                selected={selected?.kind === 'node' && selected.key === node.key}
                error={errorKeys.has(node.key)}
                onSelect={onSelectNode}
              />
            ))}
          </g>
        </svg>

        {model.nodes.length === 0 ? (
          <div
            style={{
              position: 'absolute',
              inset: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#bfbfbf',
              pointerEvents: 'none',
            }}
          >
            从左侧拖入节点开始绘制流程
          </div>
        ) : null}

        {linkFrom ? (
          <div
            style={{
              position: 'absolute',
              top: 8,
              left: 8,
              padding: '2px 8px',
              borderRadius: 4,
              background: 'rgba(22,119,255,0.1)',
              color: '#1677ff',
              fontSize: 12,
            }}
          >
            连线中：点击目标节点完成连线（点击空白取消）
          </div>
        ) : null}

        <div
          onPointerDown={(event) => event.stopPropagation()}
          onClick={(event) => event.stopPropagation()}
          style={{
            position: 'absolute',
            right: 12,
            bottom: 12,
            display: 'flex',
            alignItems: 'center',
            gap: 4,
            padding: '4px 6px',
            borderRadius: 6,
            background: 'rgba(255,255,255,0.92)',
            border: '1px solid #e5e5e5',
            fontSize: 12,
          }}
        >
          <button type="button" title="缩小" onClick={() => applyZoom(zoom / ZOOM_STEP)}>
            −
          </button>
          <span style={{ minWidth: 40, textAlign: 'center' }}>{Math.round(zoom * 100)}%</span>
          <button type="button" title="放大" onClick={() => applyZoom(zoom * ZOOM_STEP)}>
            +
          </button>
          <button type="button" title="适应画布" onClick={fitCanvas}>
            适应
          </button>
          <button type="button" title="重置为 100%" onClick={() => applyZoom(1)}>
            重置
          </button>
        </div>
        </div>
      </div>

      <DragOverlay dropAnimation={null}>
        {activeType ? (
          <div
            style={{
              padding: '4px 10px',
              border: `2px solid ${NODE_TYPE_COLORS[activeType]}`,
              borderRadius: 4,
              background: '#fff',
              fontSize: 13,
            }}
          >
            {NODE_TYPE_LABELS[activeType]}
          </div>
        ) : null}
      </DragOverlay>
    </DndContext>
  );
}
