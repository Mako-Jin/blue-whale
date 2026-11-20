// 租户成员页（Wave 5.54 Phase 5.3）
// 本租户启用成员 + 租户内角色分配；绑定/解绑关系仅平台租户（sys:user:tenant）

import { App, Form, Input, Select, Space, Tag, type TableProps } from 'antd';
import { useCallback, useRef, useState } from 'react';
import {
  AuthButton,
  FormModal,
  ProTable,
  SearchForm,
  usePermission,
  type ProTableRef,
} from '@bluewhale/whale-ui';
import { pageRoles, type RoleVO } from '../../api/role';
import {
  assignTenantMemberRoles,
  bindUserTenant,
  listTenantMembers,
  unbindUserTenant,
  type TenantMemberVO,
} from '../../api/tenant';
import { getCurrentUser } from '../../api/token';
import { useI18n } from '../../i18n';

interface MemberQuery {
  keyword?: string;
}

interface AssignValues {
  roleIds?: string[];
}

interface BindValues {
  userId: string;
}

const ROLE_PAGE_SIZE = 200;

export default function TenantMemberList() {
  const { message, modal } = App.useApp();
  const { has } = usePermission();
  const { t } = useI18n();
  const tableRef = useRef<ProTableRef>(null);
  const rawTenantId = getCurrentUser()?.tenantId;
  // 雪花 ID 走 string：不做 Number() 转换，避免超过 2^53 后静默丢精度
  const currentTenantId = rawTenantId ? String(rawTenantId) : undefined;

  const [keyword, setKeyword] = useState<string | undefined>(undefined);

  const [assignOpen, setAssignOpen] = useState(false);
  const [assignRecord, setAssignRecord] = useState<TenantMemberVO | null>(null);
  const [assignForm] = Form.useForm<AssignValues>();
  const [roles, setRoles] = useState<RoleVO[]>([]);

  const [bindOpen, setBindOpen] = useState(false);
  const [bindForm] = Form.useForm<BindValues>();

  const showError = (error: unknown, fallback: string) => {
    message.error(error instanceof Error ? error.message : fallback);
  };

  const loadMembers = useCallback(
    async (params: MemberQuery & { current: number; size: number }) => {
      const list = await listTenantMembers();
      const kw = params.keyword?.trim().toLowerCase();
      const records = kw
        ? list.filter(
            (item) =>
              item.username.toLowerCase().includes(kw) ||
              (item.nickname ?? '').toLowerCase().includes(kw) ||
              (item.phone ?? '').includes(kw),
          )
        : list;
      // 本地分页也与运行时对齐：total/size/current 为字符串
      return {
        records,
        total: String(records.length),
        current: String(params.current),
        size: String(params.size),
      };
    },
    [],
  );

  const openAssign = async (row: TenantMemberVO) => {
    setAssignRecord(row);
    setAssignOpen(true);
    if (roles.length === 0) {
      try {
        const page = await pageRoles({ current: 1, size: ROLE_PAGE_SIZE });
        setRoles(page.records);
      } catch (error) {
        showError(error, t('tenant.member.rolesLoadFailed'));
      }
    }
  };

  const handleAssign = async (values: AssignValues) => {
    if (!assignRecord) {
      return;
    }
    try {
      await assignTenantMemberRoles(assignRecord.userId, values.roleIds ?? []);
      message.success(t('tenant.member.rolesUpdated'));
      setAssignOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, t('tenant.member.assignFailed'));
      throw error;
    }
  };

  const handleBind = async (values: BindValues) => {
    if (!currentTenantId) {
      message.error(t('tenant.member.missingTenant'));
      return;
    }
    try {
      await bindUserTenant(values.userId, currentTenantId);
      message.success(t('tenant.member.bound'));
      setBindOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, t('tenant.member.bindFailed'));
      throw error;
    }
  };

  const handleUnbind = (row: TenantMemberVO) => {
    if (!currentTenantId) {
      message.error(t('tenant.member.missingTenant'));
      return;
    }
    modal.confirm({
      title: t('tenant.member.removeTitle'),
      content: t('tenant.member.removeContent', { name: row.username }),
      okText: t('tenant.action.remove'),
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await unbindUserTenant(row.userId, currentTenantId);
          message.success(t('tenant.member.removed'));
          tableRef.current?.reload();
        } catch (error) {
          showError(error, t('tenant.member.removeFailed'));
        }
      },
    });
  };

  const columns: TableProps<TenantMemberVO>['columns'] = [
    { title: t('tenant.member.col.userId'), dataIndex: 'userId', width: 100 },
    { title: t('tenant.member.col.username'), dataIndex: 'username', width: 160 },
    {
      title: t('tenant.member.col.nickname'),
      dataIndex: 'nickname',
      width: 160,
      render: (value: string | undefined) => value || '-',
    },
    {
      title: t('tenant.member.col.phone'),
      dataIndex: 'phone',
      width: 150,
      render: (value: string | undefined) => value || '-',
    },
    {
      title: t('tenant.col.status'),
      dataIndex: 'status',
      width: 90,
      render: (value: number | undefined) =>
        value === 0 ? (
          <Tag color="red">{t('tenant.status.disabled')}</Tag>
        ) : (
          <Tag color="green">{t('tenant.status.enabled')}</Tag>
        ),
    },
    {
      title: t('tenant.member.col.roles'),
      dataIndex: 'roles',
      render: (value: TenantMemberVO['roles']) =>
        value.length === 0 ? (
          <span>{t('tenant.member.unassigned')}</span>
        ) : (
          <Space size={4} wrap>
            {value.map((role) => (
              <Tag key={role.id} color="blue">
                {role.roleName}
              </Tag>
            ))}
          </Space>
        ),
    },
    {
      title: t('tenant.col.action'),
      key: 'action',
      width: 180,
      render: (_, row) => (
        <Space size={4}>
          <AuthButton
            perm="sys:tenant:member:assign"
            type="link"
            size="small"
            onClick={() => void openAssign(row)}
          >
            {t('tenant.member.assign')}
          </AuthButton>
          <AuthButton
            perm="sys:user:tenant"
            type="link"
            size="small"
            danger
            onClick={() => handleUnbind(row)}
          >
            {t('tenant.action.remove')}
          </AuthButton>
        </Space>
      ),
    },
  ];

  return (
    <>
      <ProTable<TenantMemberVO, MemberQuery>
        ref={tableRef}
        rowKey="userId"
        title={t('tenant.member.title')}
        columns={columns}
        request={loadMembers}
        params={{ keyword }}
        onError={(error) => showError(error, t('tenant.member.loadFailed'))}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: t('tenant.search.keyword'),
                element: <Input allowClear placeholder={t('tenant.member.searchPlaceholder')} />,
              },
            ]}
            onSearch={(values) => setKeyword((values.keyword as string) || undefined)}
            onReset={() => setKeyword(undefined)}
          />
        }
        toolbar={
          has('sys:user:tenant') ? (
            <AuthButton perm="sys:user:tenant" type="primary" onClick={() => setBindOpen(true)}>
              {t('tenant.member.bind')}
            </AuthButton>
          ) : null
        }
      />

      <FormModal<AssignValues>
        open={assignOpen}
        title={t('tenant.member.assignTitle', { name: assignRecord?.username ?? '' })}
        form={assignForm}
        width={520}
        initialValues={{ roleIds: assignRecord?.roles.map((role) => role.id) }}
        onOk={handleAssign}
        onCancel={() => setAssignOpen(false)}
      >
        <Form.Item name="roleIds" label={t('tenant.member.roleLabel')}>
          <Select
            mode="multiple"
            allowClear
            placeholder={t('tenant.member.rolePlaceholder')}
            options={roles.map((role) => ({
              value: role.id,
              label: `${role.roleName}（${role.roleCode}）`,
            }))}
          />
        </Form.Item>
      </FormModal>

      <FormModal<BindValues>
        open={bindOpen}
        title={t('tenant.member.bindTitle')}
        form={bindForm}
        width={480}
        onOk={handleBind}
        onCancel={() => setBindOpen(false)}
      >
        <Form.Item
          name="userId"
          label={t('tenant.member.userIdLabel')}
          rules={[{ required: true, message: t('tenant.member.userIdRequired') }]}
        >
          <Input style={{ width: '100%' }} placeholder={t('tenant.member.userIdPlaceholder')} />
        </Form.Item>
      </FormModal>
    </>
  );
}
