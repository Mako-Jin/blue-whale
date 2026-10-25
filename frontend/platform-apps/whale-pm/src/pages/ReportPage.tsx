// 报表：燃尽、速率、累积流与成员负载（只读）

import { useCallback, useEffect, useState } from 'react';
import { App, Card, DatePicker, Empty, Select, Space, Table, Tabs, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { Dayjs } from 'dayjs';
import { useParams } from 'react-router-dom';
import {
  reportApi,
  sprintApi,
  type Burndown,
  type BurndownPoint,
  type CumulativeFlow,
  type Sprint,
  type VelocitySprint,
  type WorkloadMember,
} from '../api/pm';
import { formatDate } from '../utils';

/** 燃尽图折线（纯 SVG，避免引入图表依赖） */
function BurndownChart({ points }: { points: BurndownPoint[] }) {
  if (points.length === 0) {
    return <Empty description="暂无燃尽数据" />;
  }
  const width = 680;
  const height = 260;
  const pad = 36;
  const maxValue = Math.max(
    ...points.map((point) => Math.max(Number(point.remainingPoints), Number(point.idealPoints))),
    1,
  );
  const step = points.length > 1 ? (width - pad * 2) / (points.length - 1) : 0;
  const x = (index: number) => pad + index * step;
  const y = (value: number) => height - pad - (value / maxValue) * (height - pad * 2);
  const remaining = points.map((point, index) => `${x(index)},${y(Number(point.remainingPoints))}`).join(' ');
  const ideal = points.map((point, index) => `${x(index)},${y(Number(point.idealPoints))}`).join(' ');
  return (
    <svg viewBox={`0 0 ${width} ${height}`} style={{ width: '100%', maxWidth: width }}>
      <line x1={pad} y1={height - pad} x2={width - pad} y2={height - pad} stroke="#d9d9d9" />
      <line x1={pad} y1={pad} x2={pad} y2={height - pad} stroke="#d9d9d9" />
      <polyline points={ideal} fill="none" stroke="#91caff" strokeDasharray="6 4" strokeWidth={2} />
      <polyline points={remaining} fill="none" stroke="#1677ff" strokeWidth={2} />
      <text x={pad} y={20} fill="#1677ff">剩余故事点：{points[points.length - 1].remainingPoints}</text>
    </svg>
  );
}

export default function ReportPage() {
  const { projectId = '' } = useParams();
  const { message } = App.useApp();
  const [sprints, setSprints] = useState<Sprint[]>([]);
  const [sprintId, setSprintId] = useState<string>();
  const [burndown, setBurndown] = useState<Burndown | null>(null);
  const [velocity, setVelocity] = useState<VelocitySprint[]>([]);
  const [flow, setFlow] = useState<CumulativeFlow | null>(null);
  const [workload, setWorkload] = useState<WorkloadMember[]>([]);
  const [range, setRange] = useState<[Dayjs, Dayjs] | null>(null);

  const loadBurndown = useCallback(async () => {
    try {
      setBurndown(await reportApi.burndown(projectId, sprintId));
    } catch (error) {
      message.error((error as Error).message);
    }
  }, [projectId, sprintId, message]);

  useEffect(() => {
    void loadBurndown();
  }, [loadBurndown]);

  useEffect(() => {
    Promise.all([
      sprintApi.page({ projectId, current: 1, size: 100 }),
      reportApi.velocity(projectId),
      reportApi.workload(projectId),
    ])
      .then(([sprintPage, velocityData, workloadData]) => {
        setSprints(sprintPage.records);
        setVelocity(velocityData.sprints);
        setWorkload(workloadData.members);
      })
      .catch((error: Error) => message.error(error.message));
  }, [projectId, message]);

  const loadFlow = useCallback(async () => {
    try {
      setFlow(
        await reportApi.cumulativeFlow(projectId, {
          sprintId,
          startDate: range?.[0]?.format('YYYY-MM-DD'),
          endDate: range?.[1]?.format('YYYY-MM-DD'),
        }),
      );
    } catch (error) {
      message.error((error as Error).message);
    }
  }, [projectId, sprintId, range, message]);

  useEffect(() => {
    void loadFlow();
  }, [loadFlow]);

  const velocityColumns: ColumnsType<VelocitySprint> = [
    { title: '迭代', dataIndex: 'sprintName' },
    { title: '区间', key: 'range', render: (_, record) => `${formatDate(record.startDate)} ~ ${formatDate(record.endDate)}` },
    { title: '承诺故事点', dataIndex: 'committedPoints', width: 140 },
    { title: '完成故事点', dataIndex: 'completedPoints', width: 140 },
    {
      title: '完成率',
      key: 'rate',
      width: 120,
      render: (_, record) =>
        record.committedPoints > 0
          ? `${Math.round((Number(record.completedPoints) / Number(record.committedPoints)) * 100)}%`
          : '-',
    },
  ];

  const workloadColumns: ColumnsType<WorkloadMember> = [
    { title: '负责人', dataIndex: 'assigneeId', render: (value?: string) => value ?? '未指派' },
    { title: '总数', dataIndex: 'totalCount', width: 100 },
    { title: '未完成', dataIndex: 'openCount', width: 100 },
    { title: '已完成', dataIndex: 'doneCount', width: 100 },
    { title: '未完成故事点', dataIndex: 'openPoints', width: 140 },
    { title: '已完成故事点', dataIndex: 'donePoints', width: 140 },
  ];

  const flowColumns = flow
    ? [
        {
          title: '日期',
          dataIndex: 'date',
          width: 140,
          render: formatDate,
        },
        ...flow.series.map((series) => ({
          title: series.category,
          key: series.category,
          width: 140,
          render: (_: unknown, record: { date: string; values: Record<string, number> }) =>
            record.values[series.category] ?? 0,
        })),
      ]
    : [];

  const flowRows = flow
    ? flow.dates.map((date, index) => ({
        key: date,
        date,
        values: Object.fromEntries(flow.series.map((series) => [series.category, series.values[index]])),
      }))
    : [];

  return (
    <Card>
      <Space style={{ marginBottom: 12 }} wrap>
        <Select
          allowClear
          style={{ minWidth: 200 }}
          placeholder="迭代（默认进行中）"
          value={sprintId}
          options={sprints.map((sprint) => ({ value: sprint.id, label: sprint.name }))}
          onChange={setSprintId}
        />
      </Space>
      <Tabs
        items={[
          {
            key: 'burndown',
            label: '燃尽',
            children: (
              <div>
                <Space style={{ marginBottom: 8 }}>
                  <Typography.Text strong>{burndown?.sprintName ?? '无进行中迭代'}</Typography.Text>
                  <Tag>承诺 {burndown?.totalPoints ?? 0}</Tag>
                  {burndown?.startDate && <Typography.Text type="secondary">{formatDate(burndown.startDate)}</Typography.Text>}
                  {burndown?.endDate && <Typography.Text type="secondary">~ {formatDate(burndown.endDate)}</Typography.Text>}
                </Space>
                <BurndownChart points={burndown?.points ?? []} />
              </div>
            ),
          },
          {
            key: 'velocity',
            label: '速率',
            children: (
              <Table
                rowKey="sprintId"
                size="small"
                columns={velocityColumns}
                dataSource={velocity}
                pagination={false}
              />
            ),
          },
          {
            key: 'flow',
            label: '累积流',
            children: (
              <div>
                <Space style={{ marginBottom: 12 }}>
                  <DatePicker.RangePicker value={range} onChange={(value) => setRange(value as [Dayjs, Dayjs] | null)} />
                </Space>
                <Table
                  rowKey="key"
                  size="small"
                  columns={flowColumns}
                  dataSource={flowRows}
                  pagination={{ pageSize: 10 }}
                />
              </div>
            ),
          },
          {
            key: 'workload',
            label: '负载',
            children: (
              <Table
                rowKey={(record) => record.assigneeId ?? 'unassigned'}
                size="small"
                columns={workloadColumns}
                dataSource={workload}
                pagination={false}
              />
            ),
          },
        ]}
      />
    </Card>
  );
}
