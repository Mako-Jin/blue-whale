// 画布节点图形（SVG）：按节点类型区分形状与配色，显示 name（与 USER_TASK 的 assignee）。
// 以 (0,0) 为左上角绘制，外层 <g> 负责按 x/y 平移（见 Canvas.tsx）。

import type { ReactElement } from 'react';
import type { CanvasNode } from './model';
import { NODE_SIZE, NODE_TYPE_COLORS, NODE_TYPE_LABELS } from './model';

/** 超出可视宽度的文本截断（SVG 无自动省略） */
function truncate(text: string, max: number): string {
  return text.length > max ? `${text.slice(0, max - 1)}…` : text;
}

interface NodeShapeProps {
  node: CanvasNode;
  selected?: boolean;
  error?: boolean;
}

export default function NodeShape({ node, selected = false, error = false }: NodeShapeProps) {
  const { width, height } = NODE_SIZE[node.type];
  const cx = width / 2;
  const cy = height / 2;
  const base = NODE_TYPE_COLORS[node.type];
  const stroke = error ? '#ff4d4f' : selected ? '#1677ff' : base;
  const strokeWidth = error || selected ? 3 : 2;
  const fill = '#fff';
  const name = node.name || NODE_TYPE_LABELS[node.type];

  let shape: ReactElement;
  switch (node.type) {
    case 'START_EVENT':
      shape = <circle cx={cx} cy={cy} r={cx - 3} fill={fill} stroke={stroke} strokeWidth={strokeWidth} />;
      break;
    case 'END_EVENT':
      shape = (
        <>
          <circle cx={cx} cy={cy} r={cx - 3} fill={fill} stroke={stroke} strokeWidth={strokeWidth} />
          <circle cx={cx} cy={cy} r={cx - 9} fill="none" stroke={stroke} strokeWidth={1.5} />
        </>
      );
      break;
    case 'EXCLUSIVE_GATEWAY':
      shape = (
        <polygon
          points={`${cx},3 ${width - 3},${cy} ${cx},${height - 3} 3,${cy}`}
          fill={fill}
          stroke={stroke}
          strokeWidth={strokeWidth}
        />
      );
      break;
    case 'PARALLEL_GATEWAY':
      shape = (
        <>
          <polygon
            points={`${cx},3 ${width - 3},${cy} ${cx},${height - 3} 3,${cy}`}
            fill={fill}
            stroke={stroke}
            strokeWidth={strokeWidth}
          />
          <path
            d={`M ${cx - 8} ${cy} L ${cx + 8} ${cy} M ${cx} ${cy - 8} L ${cx} ${cy + 8}`}
            stroke={stroke}
            strokeWidth={2}
            fill="none"
          />
        </>
      );
      break;
    case 'COUNTER_SIGN':
      shape = (
        <>
          <rect
            x={1.5}
            y={1.5}
            width={width - 3}
            height={height - 3}
            rx={8}
            fill={fill}
            stroke={stroke}
            strokeWidth={strokeWidth}
          />
          <rect
            x={5.5}
            y={5.5}
            width={width - 11}
            height={height - 11}
            rx={5}
            fill="none"
            stroke={stroke}
            strokeWidth={1}
          />
        </>
      );
      break;
    default:
      shape = (
        <rect
          x={1.5}
          y={1.5}
          width={width - 3}
          height={height - 3}
          rx={8}
          fill={fill}
          stroke={stroke}
          strokeWidth={strokeWidth}
        />
      );
      break;
  }

  // USER_TASK / COUNTER_SIGN 文本内嵌；圆形/菱形文本置于图形下方，避免与图形重叠
  const inlineText = node.type === 'USER_TASK' || node.type === 'COUNTER_SIGN';
  const subText =
    node.type === 'COUNTER_SIGN'
      ? `${node.assignees.length} 人会签 · ${node.counterSignPolicy || 'ALL'}`
      : node.assignee
        ? `@${truncate(node.assignee, 16)}`
        : '';

  return (
    <g>
      {shape}
      {inlineText ? (
        <>
          <text x={cx} y={subText ? cy - 6 : cy} textAnchor="middle" dominantBaseline="middle" fontSize={13} fill="#1f1f1f">
            {truncate(name, 18)}
          </text>
          {subText ? (
            <text x={cx} y={height - 14} textAnchor="middle" dominantBaseline="middle" fontSize={11} fill="#8c8c8c">
              {subText}
            </text>
          ) : null}
        </>
      ) : (
        <text x={cx} y={height + 14} textAnchor="middle" fontSize={12} fill="#595959">
          {truncate(name, node.type === 'EXCLUSIVE_GATEWAY' || node.type === 'PARALLEL_GATEWAY' ? 8 : 10)}
        </text>
      )}
    </g>
  );
}
