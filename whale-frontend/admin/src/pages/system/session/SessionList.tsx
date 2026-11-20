// 会话与设备管理页 - 会话列表（按用户过滤 / 仅活跃）+ 强制下线
// 权限码：sys:session:list(2409) / sys:session:revoke(2410)

import { App, Button, Input, Popconfirm, Select, Space, Tag, type TableProps } from 'antd';
import { useRef, useState } from 'react';
import { ProTable, SearchForm, usePermission, type ProTableRef } from '@bluewhale/whale-ui';
import { pageSessions, revokeSession, type SessionVO } from '../../../api/session';
import { useI18n } from '../../../i18n';

type SessionQuery = { userId?: string; onlyActive?: boolean };

export default function SessionList() {
  const { message } = App.useApp();
  const { t } = useI18n();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);

  const renderRevoked = (row: SessionVO) => {
    if (!row.revoked) {
      return <Tag color="processing">{t('systemOps.session.active')}</Tag>;
    }
    return (
      <Space size={4}>
        <Tag color="default">{t('systemOps.session.revoked')}</Tag>
        {row.revokedReason ? <Tag color="orange">{row.revokedReason}</Tag> : null}
      </Space>
    );
  };

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
      message.success(t('systemOps.session.revokeSuccess', { jti: row.jti }));
      tableRef.current?.reload();
    } catch (error) {
      showError(error, t('systemOps.session.revokeFailed'));
    } finally {
      setRevoking(null);
    }
  };

  const columns: TableProps<SessionVO>['columns'] = [
    { title: 'JTI', dataIndex: 'jti', width: 280, ellipsis: true },
    { title: t('systemOps.session.col.userId'), dataIndex: 'userId', width: 130 },
    {
      title: t('systemOps.session.col.family'),
      dataIndex: 'familyId',
      width: 160,
      ellipsis: true,
      render: (v) => v ?? '-',
    },
    {
      title: t('systemOps.session.col.issuedAt'),
      dataIndex: 'issuedAt',
      width: 170,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('systemOps.session.col.expiresAt'),
      dataIndex: 'expiresAt',
      width: 170,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('systemOps.common.status'),
      key: 'status',
      width: 180,
      render: (_, row) => renderRevoked(row),
    },
    {
      title: t('systemOps.common.action'),
      key: 'action',
      width: 110,
      render: (_, row) =>
        row.revoked || !has('sys:session:revoke') ? (
          '-'
        ) : (
          <Popconfirm
            title={t('systemOps.session.confirm.revokeTitle')}
            description={t('systemOps.session.confirm.revokeDesc')}
            onConfirm={() => void handleRevoke(row)}
          >
            <Button type="link" size="small" danger loading={revoking === row.jti}>
              {t('systemOps.session.action.revoke')}
            </Button>
          </Popconfirm>
        ),
    },
  ];

  return (
    <ProTable<SessionVO, SessionQuery>
      ref={tableRef}
      rowKey="jti"
      title={t('systemOps.session.title')}
      columns={columns}
      request={(params) => pageSessions({ ...params, onlyActive, userId })}
      params={{ userId }}
      onError={(error) => showError(error, t('systemOps.session.loadFailed'))}
      search={
        <SearchForm
          items={[
            {
              name: 'userId',
              label: t('systemOps.session.col.userId'),
              element: (
                <Input
                  style={{ width: 200 }}
                  placeholder={t('systemOps.session.search.userPlaceholder')}
                />
              ),
            },
            {
              name: 'onlyActive',
              label: t('systemOps.session.search.onlyActive'),
              element: (
                <Select
                  style={{ width: 120 }}
                  options={[
                    { value: true, label: t('systemOps.session.search.onlyActive') },
                    { value: false, label: t('systemOps.common.all') },
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
