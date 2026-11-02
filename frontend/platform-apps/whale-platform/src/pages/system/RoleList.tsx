// 角色管理（只读）：分页 + 关键字查询；授权调整仍由管理端 whale-admin 承担
import { useCallback, useEffect, useState } from 'react';
import { App, Input, Space, Table, Tag } from 'antd';
import type { TableProps } from 'antd';
import { pageRoles, type RoleVO } from '../../api/system';

/** 角色状态文案 */
const STATUS_LABEL: Record<number, string> = { 0: '停用', 1: '启用' };

interface RoleQuery {
  current: number;
  size: number;
  keyword: string;
}

export default function RoleList() {
  const { message } = App.useApp();
  const [data, setData] = useState<RoleVO[]>([]);
  const [total, setTotal] = useState(0);
  const [query, setQuery] = useState<RoleQuery>({ current: 1, size: 10, keyword: '' });
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageRoles({
        current: query.current,
        size: query.size,
        keyword: query.keyword || undefined,
      });
      setData(result.records);
      setTotal(result.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '角色查询失败');
      setData([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [query, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const columns: TableProps<RoleVO>['columns'] = [
    { title: '角色编码', dataIndex: 'roleCode', key: 'roleCode' },
    { title: '角色名称', dataIndex: 'roleName', key: 'roleName' },
    { title: '描述', dataIndex: 'description', key: 'description' },
    { title: '排序', dataIndex: 'sortNo', key: 'sortNo', width: 80 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (value?: number) => (
        <Tag color={value === 1 ? 'success' : 'default'}>{STATUS_LABEL[value ?? -1] ?? '-'}</Tag>
      ),
    },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Input.Search
        allowClear
        placeholder="搜索角色编码 / 名称"
        style={{ maxWidth: 360 }}
        onSearch={(value) => setQuery((prev) => ({ ...prev, current: 1, keyword: value.trim() }))}
      />
      <Table<RoleVO>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        pagination={{
          current: query.current,
          pageSize: query.size,
          total,
          showSizeChanger: true,
          onChange: (page, pageSize) =>
            setQuery((prev) => ({ ...prev, current: page, size: pageSize })),
        }}
      />
    </Space>
  );
}
