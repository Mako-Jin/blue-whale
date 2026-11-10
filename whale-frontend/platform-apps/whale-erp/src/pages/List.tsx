// ERP 列表页：antd Table 展示物料列表，数据来自占位 API 层
import { useCallback, useEffect, useState } from 'react';
import { Button, Space, Table, Tag } from 'antd';
import type { TableProps } from 'antd';
import { listErpItems, type ErpItem } from '../api/erp';
import { hasPermission } from '../utils/permission';

/** 状态文案映射 */
const STATUS_LABEL: Record<ErpItem['status'], string> = {
  ENABLED: '启用',
  DISABLED: '禁用',
};

interface ListProps {
  /** 变更后刷新列表的触发器（父组件递增） */
  refreshKey: number;
  /** 点击「新增」回调 */
  onCreate: () => void;
  /** 点击行内「编辑」回调 */
  onEdit: (item: ErpItem) => void;
}

export default function List({ refreshKey, onCreate, onEdit }: ListProps) {
  const [data, setData] = useState<ErpItem[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const rows = await listErpItems();
      setData(rows);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load, refreshKey]);

  const columns: TableProps<ErpItem>['columns'] = [
    { title: '编码', dataIndex: 'code', key: 'code' },
    { title: '名称', dataIndex: 'name', key: 'name' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (value) => {
        const status = value as ErpItem['status'];
        return <Tag color={status === 'ENABLED' ? 'green' : 'default'}>{STATUS_LABEL[status]}</Tag>;
      },
    },
    { title: '备注', dataIndex: 'remark', key: 'remark' },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: (_, record) =>
        hasPermission('sys:erp:item:edit') ? (
          <Button type="link" onClick={() => onEdit(record)}>
            编辑
          </Button>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      {hasPermission('sys:erp:item:add') ? (
        <Button type="primary" onClick={onCreate}>
          新增
        </Button>
      ) : null}
      <Table<ErpItem> rowKey="id" loading={loading} columns={columns} dataSource={data} />
    </Space>
  );
}
