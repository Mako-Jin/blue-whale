// 菜单管理（只读）：树形表格展示菜单层级、路径、类型与权限码；维护仍由管理端 whale-admin 承担
import { useCallback, useEffect, useState } from 'react';
import { App, Button, Space, Table, Tag } from 'antd';
import type { TableProps } from 'antd';
import { treeMenus, type MenuVO } from '../../api/system';

/** 菜单类型文案 */
const TYPE_LABEL: Record<string, string> = { DIR: '目录', MENU: '菜单' };

export default function MenuList() {
  const { message } = App.useApp();
  const [data, setData] = useState<MenuVO[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await treeMenus());
    } catch (error) {
      message.error(error instanceof Error ? error.message : '菜单查询失败');
      setData([]);
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    void load();
  }, [load]);

  const columns: TableProps<MenuVO>['columns'] = [
    { title: '名称', dataIndex: 'name', key: 'name' },
    { title: '路径', dataIndex: 'path', key: 'path' },
    { title: '组件', dataIndex: 'component', key: 'component' },
    {
      title: '类型',
      dataIndex: 'menuType',
      key: 'menuType',
      width: 90,
      render: (value?: string) => <Tag>{TYPE_LABEL[value ?? ''] ?? value ?? '-'}</Tag>,
    },
    { title: '权限码', dataIndex: 'permissionCode', key: 'permissionCode' },
    { title: '排序', dataIndex: 'sort', key: 'sort', width: 80 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (value?: number) => (
        <Tag color={value === 1 ? 'success' : 'default'}>{value === 1 ? '启用' : '停用'}</Tag>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Button onClick={() => void load()}>刷新</Button>
      <Table<MenuVO>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        pagination={false}
        childrenColumnName="children"
      />
    </Space>
  );
}
