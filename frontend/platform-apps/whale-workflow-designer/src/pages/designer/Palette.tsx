// 左侧节点调色板：6 类节点，@dnd-kit useDraggable 拖入画布（data.source = 'palette'）。
// 新节点落点坐标由 Canvas 的 onDragEnd 换算（见 Canvas.tsx）。

import { Divider, Tag, Typography } from 'antd';
import { useDraggable } from '@dnd-kit/core';
import type { NodeType } from '../../api/workflow';
import { NODE_TYPE_COLORS, NODE_TYPE_LABELS, NODE_SIZE } from './model';

/** 调色板拖拽源 id 前缀（Canvas 依 data 判别来源，前缀仅用于 id 唯一） */
export const PALETTE_DRAG_PREFIX = 'palette-';

/** 调色板节点顺序（6 类） */
const PALETTE_TYPES: NodeType[] = [
  'START_EVENT',
  'END_EVENT',
  'USER_TASK',
  'EXCLUSIVE_GATEWAY',
  'PARALLEL_GATEWAY',
  'COUNTER_SIGN',
];

/** 调色板条目：可拖拽源 */
function PaletteItem({ type }: { type: NodeType }) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: `${PALETTE_DRAG_PREFIX}${type}`,
    data: { source: 'palette', nodeType: type },
  });
  const color = NODE_TYPE_COLORS[type];
  const size = NODE_SIZE[type];
  return (
    <div
      ref={setNodeRef}
      {...listeners}
      {...attributes}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        padding: '8px 10px',
        marginBottom: 8,
        border: '1px solid #f0f0f0',
        borderRadius: 6,
        background: '#fff',
        cursor: isDragging ? 'grabbing' : 'grab',
        opacity: isDragging ? 0.4 : 1,
      }}
    >
      <span
        style={{
          width: 24,
          height: 14,
          borderRadius:
            type === 'START_EVENT' || type === 'END_EVENT'
              ? 999
              : type === 'EXCLUSIVE_GATEWAY' || type === 'PARALLEL_GATEWAY'
                ? 2
                : 3,
          border:
            type === 'COUNTER_SIGN' ? `3px double ${color}` : `2px solid ${color}`,
          transform:
            type === 'EXCLUSIVE_GATEWAY' || type === 'PARALLEL_GATEWAY'
              ? 'rotate(45deg)'
              : undefined,
          flex: '0 0 auto',
        }}
      />
      <span style={{ flex: 1 }}>{NODE_TYPE_LABELS[type]}</span>
      <Tag style={{ marginInlineEnd: 0 }}>{`${size.width}×${size.height}`}</Tag>
    </div>
  );
}

export default function Palette() {
  return (
    <div>
      <Divider orientation="left" style={{ marginTop: 0 }}>
        节点
      </Divider>
      {PALETTE_TYPES.map((type) => (
        <PaletteItem key={type} type={type} />
      ))}
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        拖拽节点到画布；选中源节点后点击「连线」再点目标节点即可建流。
      </Typography.Text>
    </div>
  );
}
