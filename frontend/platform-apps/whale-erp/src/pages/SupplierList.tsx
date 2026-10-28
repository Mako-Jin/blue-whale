// 供应商列表页：ERP 供应商主数据（不是 CRM 客户）
import { useCallback, useEffect, useState } from 'react';
import { Button, Popconfirm, Space, Table, Tag, message } from 'antd';
import type { TableProps } from 'antd';
import {
  deleteErpSupplier,
  listErpSuppliers,
  type ErpSupplier,
  type ErpSupplierCategory,
} from '../api/erp';
import { hasPermission } from '../utils/permission';

/** 分类文案映射 */
const CATEGORY_LABEL: Record<ErpSupplierCategory, string> = {
  MATERIAL: '物料',
  SERVICE: '服务',
  OUTSOURCE: '外协',
  LOGISTICS: '物流',
  OTHER: '其他',
};

/** 状态文案映射 */
const STATUS_LABEL: Record<ErpSupplier['status'], string> = {
  ENABLED: '启用',
  DISABLED: '禁用',
};

interface SupplierListProps {
  /** 变更后刷新列表的触发器（父组件递增） */
  refreshKey: number;
  /** 点击「新增」回调 */
  onCreate: () => void;
  /** 点击行内「编辑」回调 */
  onEdit: (supplier: ErpSupplier) => void;
}

export default function SupplierList({ refreshKey, onCreate, onEdit }: SupplierListProps) {
  const [data, setData] = useState<ErpSupplier[]>([]);
  const [loading, setLoading] = useState(false);
  const [version, setVersion] = useState(0);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await listErpSuppliers());
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load, refreshKey, version]);

  const handleDelete = async (id: string) => {
    await deleteErpSupplier(id);
    message.success('删除成功');
    setVersion((value) => value + 1);
  };

  const columns: TableProps<ErpSupplier>['columns'] = [
    { title: '编码', dataIndex: 'code', key: 'code', width: 120 },
    { title: '名称', dataIndex: 'name', key: 'name' },
    {
      title: '分类',
      dataIndex: 'category',
      key: 'category',
      width: 90,
      render: (value) => <Tag>{CATEGORY_LABEL[value as ErpSupplierCategory]}</Tag>,
    },
    { title: '联系人', dataIndex: 'contactName', key: 'contactName', width: 100 },
    { title: '联系电话', dataIndex: 'contactPhone', key: 'contactPhone', width: 130 },
    {
      title: '评级',
      dataIndex: 'rating',
      key: 'rating',
      width: 80,
      render: (value) => (value ? <Tag color="blue">{value}</Tag> : '-'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (value) => {
        const status = value as ErpSupplier['status'];
        return <Tag color={status === 'ENABLED' ? 'green' : 'default'}>{STATUS_LABEL[status]}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_, record) => (
        <Space size="small">
          {hasPermission('sys:erp:supplier:edit') ? (
            <Button type="link" size="small" onClick={() => onEdit(record)}>
              编辑
            </Button>
          ) : null}
          {hasPermission('sys:erp:supplier:delete') ? (
            <Popconfirm
              title="确认删除该供应商？"
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
      {hasPermission('sys:erp:supplier:add') ? (
        <Button type="primary" onClick={onCreate}>
          新增供应商
        </Button>
      ) : null}
      <Table<ErpSupplier> rowKey="id" loading={loading} columns={columns} dataSource={data} />
    </Space>
  );
}
