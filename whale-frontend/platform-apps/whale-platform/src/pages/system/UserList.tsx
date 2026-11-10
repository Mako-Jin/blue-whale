// 用户管理（只读）：分页 + 关键字查询；写操作仍由管理端 whale-admin 承担
import { useCallback, useEffect, useState } from 'react';
import { App, Input, Space, Table, Tag } from 'antd';
import type { TableProps } from 'antd';
import { pageUsers, type UserVO } from '../../api/system';

/** 用户状态文案 */
const STATUS_LABEL: Record<number, string> = { 0: '停用', 1: '启用' };

interface UserQuery {
  current: number;
  size: number;
  keyword: string;
}

export default function UserList() {
  const { message } = App.useApp();
  const [data, setData] = useState<UserVO[]>([]);
  const [total, setTotal] = useState(0);
  const [query, setQuery] = useState<UserQuery>({ current: 1, size: 10, keyword: '' });
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageUsers({
        current: query.current,
        size: query.size,
        keyword: query.keyword || undefined,
      });
      setData(result.records);
      setTotal(result.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '用户查询失败');
      setData([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [query, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const columns: TableProps<UserVO>['columns'] = [
    { title: '用户名', dataIndex: 'username', key: 'username' },
    { title: '昵称', dataIndex: 'nickname', key: 'nickname' },
    { title: '手机号', dataIndex: 'phone', key: 'phone' },
    { title: '邮箱', dataIndex: 'email', key: 'email' },
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
        placeholder="搜索用户名 / 昵称 / 手机号"
        style={{ maxWidth: 360 }}
        onSearch={(value) => setQuery((prev) => ({ ...prev, current: 1, keyword: value.trim() }))}
      />
      <Table<UserVO>
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
