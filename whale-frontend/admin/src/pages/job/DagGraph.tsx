// DAG 拓扑图渲染：纯 SVG，节点按分层布局摆放，边为上游→下游的贝塞尔曲线。
// 不引入图库；缩放只改变 SVG 显示尺寸，容器负责滚动。

import { Button, Empty, Space, theme } from 'antd';
import { useId, useMemo, useState } from 'react';
import type { JobGraphEdgeVO, JobGraphNodeVO } from '../../api/job';
import { layoutDag } from './dagLayout';

interface DagGraphProps {
  nodes: JobGraphNodeVO[];
  edges: JobGraphEdgeVO[];
  /** 环检测命中的任务 ID（首尾重复），用于标红提示 */
  cycleJobIds?: string[];
  /** 画布高度，超出滚动 */
  height?: number;
}

/** 节点标题超长截断，完整信息放 <title> */
function truncate(text: string, max = 12): string {
  return text.length > max ? `${text.slice(0, max)}…` : text;
}

export function DagGraph({ nodes, edges, cycleJobIds = [], height = 520 }: DagGraphProps) {
  const { token } = theme.useToken();
  const [scale, setScale] = useState(1);
  const markerId = useId().replace(/:/g, '');
  const cycleSet = useMemo(() => new Set(cycleJobIds), [cycleJobIds]);
  const layout = useMemo(() => layoutDag(nodes, edges), [nodes, edges]);

  if (nodes.length === 0) {
    return (
      <div style={{ padding: 48 }}>
        <Empty description="当前分组没有任务" />
      </div>
    );
  }

  return (
    <div>
      <Space style={{ marginBottom: 8 }}>
        <Button size="small" onClick={() => setScale((value) => Math.max(0.5, value - 0.1))}>
          缩小
        </Button>
        <Button size="small" onClick={() => setScale(1)}>
          {Math.round(scale * 100)}%
        </Button>
        <Button size="small" onClick={() => setScale((value) => Math.min(1.6, value + 0.1))}>
          放大
        </Button>
      </Space>
      <div
        style={{
          height,
          overflow: 'auto',
          border: `1px solid ${token.colorBorderSecondary}`,
          borderRadius: token.borderRadiusLG,
          background: token.colorBgLayout,
        }}
      >
        <svg
          width={layout.width * scale}
          height={layout.height * scale}
          viewBox={`0 0 ${layout.width} ${layout.height}`}
          role="img"
          aria-label={`依赖拓扑图，共 ${nodes.length} 个任务、${edges.length} 条依赖`}
        >
          <defs>
            <marker
              id={markerId}
              viewBox="0 0 10 10"
              refX="10"
              refY="5"
              markerWidth="7"
              markerHeight="7"
              orient="auto-start-reverse"
            >
              <path d="M 0 0 L 10 5 L 0 10 z" fill={token.colorTextTertiary} />
            </marker>
          </defs>
          {layout.edges.map((edge) => (
            <path
              key={`${edge.from}-${edge.to}`}
              d={edge.path}
              fill="none"
              stroke={token.colorTextTertiary}
              strokeWidth={1.5}
              markerEnd={`url(#${markerId})`}
            />
          ))}
          {layout.nodes.map(({ node, x, y, width, height: boxHeight }) => {
            const inCycle = cycleSet.has(node.id);
            const running = node.status === 1;
            const hasFailure = (node.failCount ?? 0) > 0;
            const detail = hasFailure
              ? `#${node.id} · 连续失败 ${node.failCount}`
              : `#${node.id} · ${node.cronExpression ?? '无 Cron'}`;
            return (
              <g key={node.id}>
                <title>
                  {`${node.jobName}（#${node.id}）\n分组：${node.jobGroup ?? '-'}\n执行器：${
                    node.executorApp ?? '-'
                  } / ${node.jobCode ?? '-'}\nCron：${node.cronExpression ?? '-'}\n状态：${
                    running ? '运行中' : '已停止'
                  }${hasFailure ? `\n连续失败：${node.failCount}` : ''}`}
                </title>
                <rect
                  x={x}
                  y={y}
                  width={width}
                  height={boxHeight}
                  rx={8}
                  fill={token.colorBgContainer}
                  stroke={inCycle ? token.colorError : token.colorBorder}
                  strokeWidth={inCycle ? 2 : 1}
                />
                <circle
                  cx={x + 14}
                  cy={y + 17}
                  r={4}
                  fill={running ? token.colorSuccess : token.colorTextQuaternary}
                />
                <text
                  x={x + 26}
                  y={y + 21}
                  fontSize={13}
                  fill={token.colorText}
                  fontWeight={500}
                >
                  {truncate(node.jobName)}
                </text>
                <text
                  x={x + 14}
                  y={y + 38}
                  fontSize={11}
                  fill={hasFailure ? token.colorError : token.colorTextTertiary}
                >
                  {truncate(detail, 24)}
                </text>
              </g>
            );
          })}
        </svg>
      </div>
    </div>
  );
}
