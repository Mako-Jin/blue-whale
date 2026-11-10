// 画布连线（SVG path 带箭头）：由源节点底边中点连至目标节点顶边中点。
// 选中 / 报错态改变描边配色与箭头 marker；condition 文本与「默认」标签在中点展示。

import type { CanvasFlow, CanvasNode } from './model';
import { NODE_SIZE } from './model';

interface EdgePathProps {
  flow: CanvasFlow;
  from: CanvasNode;
  to: CanvasNode;
  selected?: boolean;
  error?: boolean;
  onSelect: (key: string) => void;
}

export default function EdgePath({
  flow,
  from,
  to,
  selected = false,
  error = false,
  onSelect,
}: EdgePathProps) {
  const fromSize = NODE_SIZE[from.type];
  const toSize = NODE_SIZE[to.type];
  const sx = from.x + fromSize.width / 2;
  const sy = from.y + fromSize.height;
  const tx = to.x + toSize.width / 2;
  const ty = to.y;

  let d: string;
  let labelX: number;
  let labelY: number;
  if (Math.abs(sx - tx) < 2) {
    d = `M ${sx} ${sy} L ${tx} ${ty}`;
    labelX = sx;
    labelY = (sy + ty) / 2;
  } else {
    const c1y = sy + Math.max(40, (ty - sy) / 2);
    const c2y = ty - Math.max(40, (ty - sy) / 2);
    d = `M ${sx} ${sy} C ${sx} ${c1y}, ${tx} ${c2y}, ${tx} ${ty}`;
    // 三次贝塞尔曲线 t=0.5 处的取点（用于标签定位）
    labelX = 0.125 * sx + 0.375 * sx + 0.375 * tx + 0.125 * tx;
    labelY = 0.125 * sy + 0.375 * c1y + 0.375 * c2y + 0.125 * ty;
  }

  const stroke = error ? '#ff4d4f' : selected ? '#1677ff' : '#8c8c8c';
  const markerId = error ? 'wf-arrow-error' : selected ? 'wf-arrow-selected' : 'wf-arrow';
  // SVG <text> 不换行：条件多行输入展示时折叠为单行
  const label = flow.isDefault ? '默认' : flow.condition.replace(/\s+/g, ' ').trim();

  return (
    <g>
      {/* 加宽透明命中区：便于点击选中小连线 */}
      <path
        d={d}
        fill="none"
        stroke="transparent"
        strokeWidth={14}
        style={{ cursor: 'pointer' }}
        onClick={(event) => {
          event.stopPropagation();
          onSelect(flow.key);
        }}
      />
      <path
        d={d}
        fill="none"
        stroke={stroke}
        strokeWidth={selected || error ? 2.5 : 1.5}
        markerEnd={`url(#${markerId})`}
        pointerEvents="none"
      />
      {label ? (
        <text
          x={labelX}
          y={labelY}
          textAnchor="middle"
          fontSize={11}
          fill={flow.isDefault ? '#1677ff' : '#595959'}
          stroke="#fff"
          strokeWidth={3}
          paintOrder="stroke"
          pointerEvents="none"
        >
          {label}
        </text>
      ) : null}
    </g>
  );
}
