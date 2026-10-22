// 画布渲染层（SVG）：箭头 marker 定义、对齐参考线、分组容器、连线、节点。
// 纯展示：所有交互（拖拽 / 缩放 / 平移 / 选择）由 Canvas 与 hooks 提供。

import EdgePath from '../EdgePath';
import { type CanvasModel, type Selection } from '../model';
import DraggableNode from './DraggableNode';
import { groupBounds } from './geometry';

interface CanvasSceneProps {
  model: CanvasModel;
  width: number;
  height: number;
  zoom: number;
  /** 对齐参考线（画布坐标，null 表示该轴无参考线） */
  guide: { x: number | null; y: number | null };
  selected: Selection | null;
  /** 校验 error 的 refKey 集合（节点 key 或流转 key） */
  errorKeys: Set<string>;
  onSelectNode: (key: string) => void;
  onSelectFlow: (key: string) => void;
  onClearSelection: () => void;
}

export default function CanvasScene({
  model,
  width,
  height,
  zoom,
  guide,
  selected,
  errorKeys,
  onSelectNode,
  onSelectFlow,
  onClearSelection,
}: CanvasSceneProps) {
  const nodesByKey = new Map(model.nodes.map((node) => [node.key, node]));

  return (
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
        <marker
          id="wf-arrow"
          viewBox="0 0 10 10"
          refX="9"
          refY="5"
          markerWidth="7"
          markerHeight="7"
          orient="auto-start-reverse"
        >
          <path d="M 0 0 L 10 5 L 0 10 z" fill="#8c8c8c" />
        </marker>
        <marker
          id="wf-arrow-selected"
          viewBox="0 0 10 10"
          refX="9"
          refY="5"
          markerWidth="7"
          markerHeight="7"
          orient="auto-start-reverse"
        >
          <path d="M 0 0 L 10 5 L 0 10 z" fill="#1677ff" />
        </marker>
        <marker
          id="wf-arrow-error"
          viewBox="0 0 10 10"
          refX="9"
          refY="5"
          markerWidth="7"
          markerHeight="7"
          orient="auto-start-reverse"
        >
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
  );
}
