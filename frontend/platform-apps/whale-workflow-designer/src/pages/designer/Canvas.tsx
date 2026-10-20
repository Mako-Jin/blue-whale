// 自由画布（SVG 自绘 + @dnd-kit 拖入 / 拖动）：
// - 整块画布为 droppable：从 Palette 拖入落点按 pointer 换算坐标并新建节点；
// - 节点为可拖拽源（拖动结束写回 x/y，10px 网格吸附）；
// - 连线（EdgePath）在下层，节点在上层；选中态高亮、校验 errorKeys 标红。

import {
  DndContext,
  DragOverlay,
  PointerSensor,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from '@dnd-kit/core';
import { useMemo, useRef, useState, type ReactNode } from 'react';
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

/** 画布最小尺寸与右侧 / 底部留白（内容超出时自动扩展） */
const CANVAS_MIN_WIDTH = 1200;
const CANVAS_MIN_HEIGHT = 640;
const CANVAS_PADDING = 120;

/** 拖拽源 data 结构（Palette 与画布节点共用判别字段 source） */
interface DraggableData {
  source?: 'palette' | 'node';
  nodeType?: NodeType;
  key?: string;
}

/** 坐标吸附到网格并夹取非负 */
function snap(value: number): number {
  return Math.max(0, Math.round(value / GRID) * GRID);
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

  const attachRef = (element: HTMLDivElement | null) => {
    containerRef.current = element;
    setNodeRef(element);
  };

  const handleDragStart = (event: DragStartEvent) => {
    const data = event.active.data.current as DraggableData | undefined;
    setActiveType(data?.source === 'palette' ? data.nodeType ?? null : null);
  };

  const handleDragEnd = (event: DragEndEvent) => {
    setActiveType(null);
    const data = event.active.data.current as DraggableData | undefined;

    if (data?.source === 'node' && data.key) {
      const node = nodesByKey.get(data.key);
      if (!node) {
        return;
      }
      onMoveNode(node.key, snap(node.x + event.delta.x), snap(node.y + event.delta.y));
      return;
    }

    if (data?.source === 'palette' && data.nodeType) {
      const translated = event.active.rect.current.translated;
      const container = containerRef.current;
      if (!translated || !container) {
        return;
      }
      const rect = container.getBoundingClientRect();
      // 落点 = 拖拽拖影中心（客户端坐标）→ 画布局部坐标
      const centerX = translated.left + translated.width / 2 - rect.left + container.scrollLeft;
      const centerY = translated.top + translated.height / 2 - rect.top + container.scrollTop;
      const size = NODE_SIZE[data.nodeType];
      onDropNode(data.nodeType, snap(centerX - size.width / 2), snap(centerY - size.height / 2));
    }
  };

  return (
    <DndContext
      sensors={sensors}
      onDragStart={handleDragStart}
      onDragEnd={handleDragEnd}
      onDragCancel={() => setActiveType(null)}
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
          style={{
            position: 'relative',
            flex: 1,
            minWidth: 0,
            overflow: 'auto',
            border: `1px dashed ${isOver ? '#1677ff' : '#d9d9d9'}`,
            borderRadius: 6,
            background: '#fafafa',
          }}
        >
        <svg
          width={width}
          height={height}
          viewBox={`0 0 ${width} ${height}`}
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
