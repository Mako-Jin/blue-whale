// MES 工单列表页：按编码 / ERP 单号 / 状态 / 优先级筛选，展示工单生命周期与计划信息
import { useCallback, useEffect, useState } from 'react';
import { Button, Input, Popconfirm, Select, Space, Table, Tag, message } from 'antd';
import type { TableProps } from 'antd';
import {
  deleteMesWorkOrder,
  listMesWorkOrders,
  type MesWorkOrder,
  type MesWorkOrderPriority,
  type MesWorkOrderStatus,
} from '../api/mes';
import { hasPermission } from '../utils/permission';

/** 状态文案映射 */
const STATUS_LABEL: Record<MesWorkOrderStatus, string> = {
  RECEIVED: '已接收',
  DISPATCHED: '已派工',
  IN_PROGRESS: '生产中',
  PAUSED: '已暂停',
  COMPLETED: '已完工',
  CLOSED: '已关闭',
  CANCELLED: '已取消',
};

/** 状态标签颜色 */
const STATUS_COLOR: Record<MesWorkOrderStatus, string> = {
  RECEIVED: 'blue',
  DISPATCHED: 'cyan',
  IN_PROGRESS: 'processing',
  PAUSED: 'orange',
  COMPLETED: 'green',
  CLOSED: 'default',
  CANCELLED: 'red',
};

/** 优先级文案映射 */
const PRIORITY_LABEL: Record<MesWorkOrderPriority, string> = {
  NORMAL: '普通',
  URGENT: '紧急',
};

/** 状态筛选项 */
const STATUS_FILTER = Object.entries(STATUS_LABEL).map(([value, label]) => ({ value, label }));

/** 优先级筛选项 */
const PRIORITY_FILTER = Object.entries(PRIORITY_LABEL).map(([value, label]) => ({ value, label }));

/** 允许删除的状态（与后端一致） */
const DELETABLE_STATUS: MesWorkOrderStatus[] = ['RECEIVED', 'CANCELLED'];

/** 时间展示：ISO 转 `YYYY-MM-DD HH:mm`，空值显示占位符 */
function formatTime(value?: string): string {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '-';
}

interface ListProps {
  /** 变更后刷新列表的触发器（父组件递增） */
  refreshKey: number;
  /** 点击「新增」回调 */
  onCreate: () => void;
  /** 点击行内「编辑」回调 */
  onEdit: (item: MesWorkOrder) => void;
}

export default function List({ refreshKey, onCreate, onEdit }: ListProps) {
  const [data, setData] = useState<MesWorkOrder[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<MesWorkOrderStatus | undefined>(undefined);
  const [priority, setPriority] = useState<MesWorkOrderPriority | undefined>(undefined);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const rows = await listMesWorkOrders({ code: keyword || undefined, status, priority });
      setData(rows);
    } finally {
      setLoading(false);
    }
  }, [keyword, status, priority]);

  useEffect(() => {
    void load();
  }, [load, refreshKey]);

  const handleDelete = async (id: string) => {
    try {
      await deleteMesWorkOrder(id);
      message.success('删除成功');
      await load();
    } catch {
      message.error('删除失败：仅已接收或已取消的工单允许删除');
    }
  };

  const columns: TableProps<MesWorkOrder>['columns'] = [
    { title: '工单编码', dataIndex: 'code', key: 'code', width: 130 },
    { title: '工单名称', dataIndex: 'name', key: 'name', width: 160 },
    { title: 'ERP 单号', dataIndex: 'erpOrderNo', key: 'erpOrderNo', width: 150 },
    {
      title: '物料',
      key: 'item',
      width: 180,
      render: (_, record) =>
        record.itemCode ? `${record.itemCode} ${record.itemName ?? ''}`.trim() : '-',
    },
    {
      title: '计划数量',
      key: 'plannedQuantity',
      width: 120,
      render: (_, record) =>
        record.plannedQuantity === undefined || record.plannedQuantity === null
          ? '-'
          : `${record.plannedQuantity} ${record.uom ?? ''}`.trim(),
    },
    {
      title: '优先级',
      dataIndex: 'priority',
      key: 'priority',
      width: 90,
      render: (value) => {
        const current = value as MesWorkOrderPriority | undefined;
        if (!current) return '-';
        return <Tag color={current === 'URGENT' ? 'red' : 'default'}>{PRIORITY_LABEL[current]}</Tag>;
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value) => {
        const current = value as MesWorkOrderStatus;
        return <Tag color={STATUS_COLOR[current]}>{STATUS_LABEL[current] ?? current}</Tag>;
      },
    },
    {
      title: '计划开工',
      dataIndex: 'plannedStart',
      key: 'plannedStart',
      width: 150,
      render: (value) => formatTime(value as string | undefined),
    },
    {
      title: '计划完工',
      dataIndex: 'plannedEnd',
      key: 'plannedEnd',
      width: 150,
      render: (value) => formatTime(value as string | undefined),
    },
    { title: '备注', dataIndex: 'remark', key: 'remark' },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_, record) => (
        <Space size="small">
          {hasPermission('sys:mes:workorder:edit') ? (
            <Button type="link" size="small" onClick={() => onEdit(record)}>
              编辑
            </Button>
          ) : null}
          {hasPermission('sys:mes:workorder:delete')
          && DELETABLE_STATUS.includes(record.status) ? (
            <Popconfirm
              title="确认删除该工单？"
              okText="删除"
              cancelText="取消"
              onConfirm={() => handleDelete(record.id)}
            >
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="按工单编码搜索"
          style={{ width: 240 }}
          onSearch={(value) => setKeyword(value)}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 140 }}
          options={STATUS_FILTER}
          value={status}
          onChange={(value) => setStatus(value as MesWorkOrderStatus | undefined)}
        />
        <Select
          allowClear
          placeholder="优先级"
          style={{ width: 120 }}
          options={PRIORITY_FILTER}
          value={priority}
          onChange={(value) => setPriority(value as MesWorkOrderPriority | undefined)}
        />
        {hasPermission('sys:mes:workorder:add') ? (
          <Button type="primary" onClick={onCreate}>
            新增
          </Button>
        ) : null}
      </Space>
      <Table<MesWorkOrder>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        scroll={{ x: 1400 }}
      />
    </Space>
  );
}
