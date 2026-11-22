// DAG 拓扑图渲染：纯 SVG，节点按分层布局摆放，边为上游→下游的贝塞尔曲线。
// 不引入图库；缩放只改变 SVG 显示尺寸，容器负责滚动。

import { Button, Empty, Space, theme } from 'antd';
import { useId, useMemo, useState } from 'react';
import type { JobGraphEdgeVO, JobGraphNodeVO } from '../../api/job';
import { useI18n } from '../../i18n';
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
  const { t } = useI18n();
  const [scale, setScale] = useState(1);
  const markerId = useId().replace(/:/g, '');
  const cycleSet = useMemo(() => new Set(cycleJobIds), [cycleJobIds]);
  const layout = useMemo(() => layoutDag(nodes, edges), [nodes, edges]);

  if (nodes.length === 0) {
    return (
      <div style={{ padding: 48 }}>
        <Empty description={t('job.dag.empty')} />
      </div>
    );
  }

  return (
    <div>
      <Space style={{ marginBottom: 8 }}>
        <Button size="small" onClick={() => setScale((value) => Math.max(0.5, value - 0.1))}>
          {t('job.dag.zoomOut')}
        </Button>
        <Button size="small" onClick={() => setScale(1)}>
          {Math.round(scale * 100)}%
        </Button>
        <Button size="small" onClick={() => setScale((value) => Math.min(1.6, value + 0.1))}>
          {t('job.dag.zoomIn')}
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
          aria-label={t('job.dag.aria', { jobs: nodes.length, edges: edges.length })}
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
              ? t('job.dag.nodeFailureDetail', { id: node.id, count: node.failCount ?? 0 })
              : t('job.dag.nodeCronDetail', {
                  id: node.id,
                  cron: node.cronExpression ?? t('job.dag.noCron'),
                });
            return (
              <g key={node.id}>
                <title>
                  {t('job.dag.tip', {
                    name: node.jobName,
                    id: node.id,
                    group: node.jobGroup ?? '-',
                    executor: node.executorApp ?? '-',
                    code: node.jobCode ?? '-',
                    cron: node.cronExpression ?? '-',
                    status: running ? t('job.dag.status.running') : t('job.dag.status.stopped'),
                  }) + (hasFailure ? t('job.dag.tipFailure', { count: node.failCount ?? 0 }) : '')}
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
                <text x={x + 26} y={y + 21} fontSize={13} fill={token.colorText} fontWeight={500}>
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
