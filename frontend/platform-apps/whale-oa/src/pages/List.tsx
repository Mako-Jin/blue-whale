// OA 列表页：antd Table 展示公文列表，数据来自占位 API 层
import { useCallback, useEffect, useState } from 'react';
import { Button, Space, Table, Tag } from 'antd';
import type { TableProps } from 'antd';
import { listOaDocuments, type OaDocument } from '../api/oa';

/** 状态文案映射 */
const STATUS_LABEL: Record<OaDocument['status'], string> = {
  ENABLED: '启用',
  DISABLED: '禁用',
};

interface ListProps {
  /** 变更后刷新列表的触发器（父组件递增） */
  refreshKey: number;
  /** 点击「新增」回调 */
  onCreate: () => void;
  /** 点击行内「编辑」回调 */
  onEdit: (item: OaDocument) => void;
}

export default function List({ refreshKey, onCreate, onEdit }: ListProps) {
  const [data, setData] = useState<OaDocument[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const rows = await listOaDocuments();
      setData(rows);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load, refreshKey]);

  const columns: TableProps<OaDocument>['columns'] = [
    { title: '编码', dataIndex: 'code', key: 'code' },
    { title: '名称', dataIndex: 'name', key: 'name' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (value) => {
        const status = value as OaDocument['status'];
        return <Tag color={status === 'ENABLED' ? 'green' : 'default'}>{STATUS_LABEL[status]}</Tag>;
      },
    },
    { title: '备注', dataIndex: 'remark', key: 'remark' },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: (_, record) => (
        <Button type="link" onClick={() => onEdit(record)}>
          编辑
        </Button>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Button type="primary" onClick={onCreate}>
        新增
      </Button>
      <Table<OaDocument> rowKey="id" loading={loading} columns={columns} dataSource={data} />
    </Space>
  );
}
