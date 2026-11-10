// OA 协同 - 通讯录：按姓名/账号与部门筛选，服务端分页展示组织成员
import { useCallback, useEffect, useState } from 'react';
import { App, Button, Empty, Input, Space, Table, TreeSelect } from 'antd';
import type { TableProps } from 'antd';
import {
  listDepartments,
  pageDirectoryUsers,
  type OaDeptNode,
  type OaDirectoryUser,
} from '../../api/directory';

/** 通讯录分页大小 */
const PAGE_SIZE = 20;

/** 表格列：姓名、账号、部门、手机、邮箱 */
const columns: TableProps<OaDirectoryUser>['columns'] = [
  {
    title: '姓名',
    dataIndex: 'nickname',
    key: 'nickname',
    width: 120,
    render: (_value: string | undefined, record) => record.nickname || record.username || '-',
  },
  {
    title: '账号',
    dataIndex: 'username',
    key: 'username',
    width: 140,
    render: (value?: string) => value || '-',
  },
  {
    title: '部门',
    dataIndex: 'deptName',
    key: 'deptName',
    width: 160,
    render: (_value: string | undefined, record) => record.deptName || record.deptId || '-',
  },
  {
    title: '手机',
    dataIndex: 'phone',
    key: 'phone',
    width: 140,
    render: (value?: string) => value || '-',
  },
  {
    title: '邮箱',
    dataIndex: 'email',
    key: 'email',
    render: (value?: string) => value || '-',
  },
];

export default function CollabDirectoryPage() {
  const { message } = App.useApp();

  const [depts, setDepts] = useState<OaDeptNode[]>([]);
  const [keyword, setKeyword] = useState('');
  const [deptId, setDeptId] = useState<number | undefined>(undefined);
  const [records, setRecords] = useState<OaDirectoryUser[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);

  const loadUsers = useCallback(async () => {
    setLoading(true);
    try {
      const data = await pageDirectoryUsers({
        current: page,
        size: PAGE_SIZE,
        keyword: keyword.trim() || undefined,
        deptId,
      });
      setRecords(data.records);
      setTotal(data.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '通讯录加载失败');
      setRecords([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [page, keyword, deptId, message]);

  useEffect(() => {
    void loadUsers();
  }, [loadUsers]);

  useEffect(() => {
    let active = true;
    const loadDepts = async () => {
      try {
        const nodes = await listDepartments();
        if (active) {
          setDepts(nodes);
        }
      } catch (error) {
        message.error(error instanceof Error ? error.message : '部门加载失败');
      }
    };
    void loadDepts();
    return () => {
      active = false;
    };
  }, [message]);

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="姓名/账号"
          style={{ width: 220 }}
          onSearch={(value) => {
            setKeyword(value.trim());
            setPage(1);
          }}
          onChange={(event) => {
            if (!event.target.value) {
              setKeyword('');
              setPage(1);
            }
          }}
        />
        <TreeSelect<number>
          allowClear
          showSearch
          treeDefaultExpandAll
          placeholder="全部部门"
          style={{ width: 220 }}
          value={deptId}
          treeData={depts}
          fieldNames={{ label: 'deptName', value: 'id', children: 'children' }}
          onChange={(value) => {
            setDeptId(value);
            setPage(1);
          }}
        />
        <Button loading={loading} onClick={() => void loadUsers()}>
          刷新
        </Button>
      </Space>
      <Table<OaDirectoryUser>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={records}
        scroll={{ x: 800 }}
        pagination={{
          current: page,
          pageSize: PAGE_SIZE,
          total,
          showSizeChanger: false,
          onChange: (next) => setPage(next),
        }}
        locale={{
          emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无通讯录数据" />,
        }}
      />
    </Space>
  );
}
