// MES 调度派工页：待拆分工单 + 工序任务拆分 / 派工
import { useCallback, useEffect, useState } from 'react';
import { Button, Input, Popconfirm, Select, Space, Table, Tag, Typography, message } from 'antd';
import type { TableProps } from 'antd';
import {
  dispatchMesTasks,
  listMesMasterDataOptions,
  listMesWorkOrders,
  pageMesTasks,
  splitMesWorkOrder,
  type MesMasterData,
  type MesTask,
  type MesTaskStatus,
  type MesWorkOrder,
} from '../api/mes';
import { hasPermission } from '../utils/permission';

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

/** 任务状态筛选项 */
const TASK_STATUS_FILTER = Object.entries(TASK_STATUS_LABEL).map(([value, label]) => ({ value, label }));

/** 时间展示：ISO 转 `YYYY-MM-DD HH:mm` */
function formatTime(value?: string): string {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '-';
}

/** 数量展示：空值显示占位符 */
function formatQuantity(value?: number | string): string {
  return value === undefined || value === null ? '-' : String(value);
}

/** 调度派工页 */
export default function DispatchPage() {
  const [workOrders, setWorkOrders] = useState<MesWorkOrder[]>([]);
  const [tasks, setTasks] = useState<MesTask[]>([]);
  const [lineOptions, setLineOptions] = useState<MesMasterData[]>([]);
  const [teamOptions, setTeamOptions] = useState<MesMasterData[]>([]);
  const [loading, setLoading] = useState(false);
  const [splitting, setSplitting] = useState<string | null>(null);
  const [status, setStatus] = useState<MesTaskStatus | undefined>(undefined);
  const [selectedTaskIds, setSelectedTaskIds] = useState<string[]>([]);
  const [lineId, setLineId] = useState<string | undefined>(undefined);
  const [teamId, setTeamId] = useState<string | undefined>(undefined);
  const [remark, setRemark] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [pendingOrders, taskPage] = await Promise.all([
        listMesWorkOrders({ status: 'RECEIVED' }),
        pageMesTasks({ status }),
      ]);
      setWorkOrders(pendingOrders);
      setTasks(taskPage.records);
      setSelectedTaskIds((ids) => ids.filter((id) => taskPage.records.some((row) => row.id === id)));
    } finally {
      setLoading(false);
    }
  }, [status]);

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

  const handleSplit = async (id: string) => {
    setSplitting(id);
    try {
      const created = await splitMesWorkOrder(id);
      message.success(`拆分成功，生成 ${created.length} 道工序任务`);
      await load();
    } catch {
      message.error('拆分失败：请确认工单已接收、未拆分且工艺路线已发布');
    } finally {
      setSplitting(null);
    }
  };

  const handleDispatch = async () => {
    if (!lineId) {
      message.warning('请选择派工产线');
      return;
    }
    try {
      await dispatchMesTasks({ taskIds: selectedTaskIds, lineId, teamId, remark: remark || undefined });
      message.success('派工成功');
      setSelectedTaskIds([]);
      setRemark('');
      await load();
    } catch {
      message.error('派工失败：请确认任务均处于待派工状态且工单一致');
    }
  };

  const workOrderColumns: TableProps<MesWorkOrder>['columns'] = [
    { title: '工单编码', dataIndex: 'code', key: 'code', width: 140 },
    { title: '工单名称', dataIndex: 'name', key: 'name', width: 180 },
    { title: 'ERP 单号', dataIndex: 'erpOrderNo', key: 'erpOrderNo', width: 150 },
    {
      title: '计划数量',
      key: 'plannedQuantity',
      width: 120,
      render: (_, record) => `${formatQuantity(record.plannedQuantity)} ${record.uom ?? ''}`.trim(),
    },
    {
      title: '计划开工',
      dataIndex: 'plannedStart',
      key: 'plannedStart',
      width: 150,
      render: (value) => formatTime(value as string | undefined),
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: (_, record) =>
        hasPermission('sys:mes:dispatch:split') ? (
          <Popconfirm
            title="按已发布工艺路线拆分为工序任务？"
            okText="拆分"
            cancelText="取消"
            onConfirm={() => handleSplit(record.id)}
          >
            <Button type="link" size="small" loading={splitting === record.id}>
              拆分
            </Button>
          </Popconfirm>
        ) : null,
    },
  ];

  const taskColumns: TableProps<MesTask>['columns'] = [
    { title: '任务编号', dataIndex: 'taskNo', key: 'taskNo', width: 190 },
    { title: '工单编码', dataIndex: 'workOrderCode', key: 'workOrderCode', width: 140 },
    { title: '工序顺序', dataIndex: 'seqNo', key: 'seqNo', width: 90 },
    {
      title: '工序',
      key: 'operation',
      width: 160,
      render: (_, record) => `${record.operationCode ?? ''} ${record.operationName ?? ''}`.trim() || '-',
    },
    { title: '工作中心', dataIndex: 'workCenterCode', key: 'workCenterCode', width: 130 },
    {
      title: '计划 / 已派工',
      key: 'quantity',
      width: 150,
      render: (_, record) =>
        `${formatQuantity(record.plannedQuantity)} / ${formatQuantity(record.dispatchedQuantity)}`,
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
    {
      title: '计划开工',
      dataIndex: 'plannedStart',
      key: 'plannedStart',
      width: 150,
      render: (value) => formatTime(value as string | undefined),
    },
  ];

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <section>
        <Typography.Title level={5}>待拆分工单</Typography.Title>
        <Table<MesWorkOrder>
          rowKey="id"
          loading={loading}
          columns={workOrderColumns}
          dataSource={workOrders}
          pagination={false}
          locale={{ emptyText: '暂无可拆分的已接收工单' }}
        />
      </section>
      <section>
        <Typography.Title level={5}>工序任务</Typography.Title>
        <Space wrap style={{ marginBottom: 12 }}>
          <Select
            allowClear
            placeholder="状态"
            style={{ width: 140 }}
            options={TASK_STATUS_FILTER}
            value={status}
            onChange={(value) => setStatus(value as MesTaskStatus | undefined)}
          />
          {hasPermission('sys:mes:dispatch:add') ? (
            <>
              <Select
                placeholder="派工产线"
                style={{ width: 200 }}
                showSearch
                optionFilterProp="label"
                options={lineOptions.map((item) => ({ value: item.id, label: `${item.code} ${item.name}`.trim() }))}
                value={lineId}
                onChange={(value) => setLineId(value)}
              />
              <Select
                allowClear
                placeholder="班组（可选）"
                style={{ width: 200 }}
                showSearch
                optionFilterProp="label"
                options={teamOptions.map((item) => ({ value: item.id, label: `${item.code} ${item.name}`.trim() }))}
                value={teamId}
                onChange={(value) => setTeamId(value)}
              />
              <Input
                allowClear
                placeholder="派工备注"
                style={{ width: 200 }}
                value={remark}
                onChange={(event) => setRemark(event.target.value)}
              />
              <Button
                type="primary"
                disabled={selectedTaskIds.length === 0}
                onClick={handleDispatch}
              >
                派工（已选 {selectedTaskIds.length}）
              </Button>
            </>
          ) : null}
        </Space>
        <Table<MesTask>
          rowKey="id"
          loading={loading}
          columns={taskColumns}
          dataSource={tasks}
          scroll={{ x: 1200 }}
          rowSelection={
            hasPermission('sys:mes:dispatch:add')
              ? {
                  selectedRowKeys: selectedTaskIds,
                  onChange: (keys) => setSelectedTaskIds(keys as string[]),
                  getCheckboxProps: (record) => ({ disabled: record.status !== 'PLANNED' }),
                }
              : undefined
          }
        />
      </section>
    </Space>
  );
}
