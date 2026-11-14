// 会话与设备管理页 - 会话列表（按用户过滤 / 仅活跃）+ 强制下线
// 权限码：sys:session:list(2409) / sys:session:revoke(2410)

import { App, Button, Input, Popconfirm, Select, Space, Tag, type TableProps } from 'antd';
import { useRef, useState } from 'react';
import { ProTable, SearchForm, usePermission, type ProTableRef } from '@bluewhale/whale-ui';
import { pageSessions, revokeSession, type SessionVO } from '../../../api/session';

type SessionQuery = { userId?: string; onlyActive?: boolean };

function renderRevoked(row: SessionVO) {
  if (!row.revoked) {
    return <Tag color="processing">活跃</Tag>;
  }
  return (
    <Space size={4}>
      <Tag color="default">已撤销</Tag>
      {row.revokedReason ? <Tag color="orange">{row.revokedReason}</Tag> : null}
    </Space>
  );
}

export default function SessionList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);

  const [userId, setUserId] = useState<string | undefined>(undefined);
  const [onlyActive, setOnlyActive] = useState<boolean>(false);
  const [revoking, setRevoking] = useState<string | null>(null);

  const showError = (error: unknown, fallback: string) => {
    message.error(error instanceof Error ? error.message : fallback);
  };

  const handleRevoke = async (row: SessionVO) => {
    setRevoking(row.jti);
    try {
      await revokeSession(row.jti);
      message.success(`会话 ${row.jti} 已强制下线`);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '强制下线失败');
    } finally {
      setRevoking(null);
    }
  };

  const columns: TableProps<SessionVO>['columns'] = [
    { title: 'JTI', dataIndex: 'jti', width: 280, ellipsis: true },
    { title: '用户 ID', dataIndex: 'userId', width: 130 },
    { title: '会话族', dataIndex: 'familyId', width: 160, ellipsis: true, render: (v) => v ?? '-' },
    {
      title: '签发时间',
      dataIndex: 'issuedAt',
      width: 170,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '过期时间',
      dataIndex: 'expiresAt',
      width: 170,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '状态',
      key: 'status',
      width: 180,
      render: (_, row) => renderRevoked(row),
    },
    {
      title: '操作',
      key: 'action',
      width: 110,
      render: (_, row) =>
        row.revoked || !has('sys:session:revoke') ? (
          '-'
        ) : (
          <Popconfirm
            title="确认强制下线该会话？"
            description="该用户的此令牌立即失效，需重新登录"
            onConfirm={() => void handleRevoke(row)}
          >
            <Button type="link" size="small" danger loading={revoking === row.jti}>
              强制下线
            </Button>
          </Popconfirm>
        ),
    },
  ];

  return (
    <ProTable<SessionVO, SessionQuery>
      ref={tableRef}
      rowKey="jti"
      title="会话与设备"
      columns={columns}
      request={(params) => pageSessions({ ...params, onlyActive, userId })}
      params={{ userId }}
      onError={(error) => showError(error, '会话列表加载失败')}
      search={
        <SearchForm
          items={[
            {
              name: 'userId',
              label: '用户 ID',
              element: <Input style={{ width: 200 }} placeholder="留空查全部" />,
            },
            {
              name: 'onlyActive',
              label: '仅活跃',
              element: (
                <Select
                  style={{ width: 120 }}
                  options={[
                    { value: true, label: '仅活跃' },
                    { value: false, label: '全部' },
                  ]}
                />
              ),
            },
          ]}
          onSearch={(values) => {
            setUserId((values.userId as string | undefined) || undefined);
            setOnlyActive(Boolean(values.onlyActive));
          }}
          onReset={() => {
            setUserId(undefined);
            setOnlyActive(false);
          }}
        />
      }
    />
  );
}
