// 可拖拽画布节点：@dnd-kit useDraggable 包裹 NodeShape（拖动位移由 transform 提供，
// 落位写回坐标由父层 onMoveNode 处理）。

import { useDraggable } from '@dnd-kit/core';
import NodeShape from '../NodeShape';
import type { CanvasNode } from '../model';

/** 节点拖拽源 id 前缀 */
const NODE_DRAG_PREFIX = 'wf-node-';

interface DraggableNodeProps {
  node: CanvasNode;
  selected: boolean;
  error: boolean;
  onSelect: (key: string) => void;
}

export default function DraggableNode({ node, selected, error, onSelect }: DraggableNodeProps) {
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
