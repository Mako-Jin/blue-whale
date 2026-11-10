// MES 生产看板：当日目标 / 完工 / 不良 / 达成率与异常数概览，附带执行中任务
import { useCallback, useEffect, useState } from 'react';
import { Card, Col, Row, Select, Space, Statistic, Table, Tag, Typography } from 'antd';
import type { TableProps } from 'antd';
import {
  fetchMesBoard,
  listMesMasterDataOptions,
  type MesBoard,
  type MesMasterData,
  type MesTask,
  type MesTaskStatus,
} from '../api/mes';

/** 任务状态文案 */
const TASK_STATUS_LABEL: Record<MesTaskStatus, string> = {
  PLANNED: '待派工',
  DISPATCHED: '已派工',
  IN_PROGRESS: '生产中',
  PAUSED: '已暂停',
  COMPLETED: '已完工',
  SCRAPPED: '已报废',
};

/** 任务状态标签颜色 */
const TASK_STATUS_COLOR: Record<MesTaskStatus, string> = {
  PLANNED: 'default',
  DISPATCHED: 'blue',
  IN_PROGRESS: 'processing',
  PAUSED: 'orange',
  COMPLETED: 'green',
  SCRAPPED: 'red',
};

/** 数量展示 */
function formatQuantity(value?: number | string): string {
  return value === undefined || value === null ? '-' : String(value);
}

/** 生产看板页 */
export default function BoardPage() {
  const [board, setBoard] = useState<MesBoard | null>(null);
  const [loading, setLoading] = useState(false);
  const [lineOptions, setLineOptions] = useState<MesMasterData[]>([]);
  const [teamOptions, setTeamOptions] = useState<MesMasterData[]>([]);
  const [lineId, setLineId] = useState<string | undefined>(undefined);
  const [teamId, setTeamId] = useState<string | undefined>(undefined);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setBoard(await fetchMesBoard({ lineId, teamId }));
    } finally {
      setLoading(false);
    }
  }, [lineId, teamId]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    void (async () => {
      const [lines, teams] = await Promise.all([
        listMesMasterDataOptions('line'),
        listMesMasterDataOptions('team'),
      ]);
      setLineOptions(lines);
      setTeamOptions(teams);
    })();
  }, []);

  const columns: TableProps<MesTask>['columns'] = [
    { title: '任务编号', dataIndex: 'taskNo', key: 'taskNo', width: 190 },
    { title: '工单编码', dataIndex: 'workOrderCode', key: 'workOrderCode', width: 140 },
    {
      title: '工序',
      key: 'operation',
      width: 160,
      render: (_, record) => `${record.operationCode ?? ''} ${record.operationName ?? ''}`.trim() || '-',
    },
    {
      title: '计划数量',
      dataIndex: 'plannedQuantity',
      key: 'plannedQuantity',
      width: 120,
      render: (value) => formatQuantity(value as number | string | undefined),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value) => {
        const current = value as MesTaskStatus;
        return <Tag color={TASK_STATUS_COLOR[current]}>{TASK_STATUS_LABEL[current] ?? current}</Tag>;
      },
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Typography.Title level={5} style={{ margin: 0 }}>
          生产看板 {board?.boardDate ? `（${String(board.boardDate).slice(0, 10)}）` : ''}
        </Typography.Title>
        <Select
          allowClear
          placeholder="产线"
          style={{ width: 200 }}
          showSearch
          optionFilterProp="label"
          value={lineId}
          onChange={(value) => setLineId(value)}
          options={lineOptions.map((item) => ({ value: item.id, label: `${item.code} ${item.name}`.trim() }))}
        />
        <Select
          allowClear
          placeholder="班组"
          style={{ width: 200 }}
          showSearch
          optionFilterProp="label"
          value={teamId}
          onChange={(value) => setTeamId(value)}
          options={teamOptions.map((item) => ({ value: item.id, label: `${item.code} ${item.name}`.trim() }))}
        />
      </Space>
      <Card loading={loading}>
        <Row gutter={16}>
          <Col xs={24} sm={12} md={6}>
            <Statistic title="今日目标" value={formatQuantity(board?.targetQuantity)} />
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Statistic title="今日完工" value={formatQuantity(board?.completedQuantity)} />
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Statistic title="今日不良" value={formatQuantity(board?.defectQuantity)} />
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Statistic
              title="达成率"
              value={board?.achievementRate ?? 0}
              suffix="%"
              precision={2}
            />
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Statistic title="未关闭异常" value={board?.exceptionCount ?? 0} />
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Statistic title="执行中任务" value={board?.activeTaskCount ?? 0} />
          </Col>
        </Row>
      </Card>
      <Table<MesTask>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={board?.activeTasks ?? []}
        pagination={false}
        locale={{ emptyText: '暂无执行中的工序任务' }}
      />
    </Space>
  );
}
