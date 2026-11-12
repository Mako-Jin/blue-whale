// 租户成员页（Wave 5.54 Phase 5.3）
// 本租户启用成员 + 租户内角色分配；绑定/解绑关系仅平台租户（sys:user:tenant）

import { App, Form, Input, InputNumber, Select, Space, Tag, type TableProps } from 'antd';
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

interface MemberQuery {
  keyword?: string;
}

interface AssignValues {
  roleIds?: number[];
}

interface BindValues {
  userId: number;
}

const ROLE_PAGE_SIZE = 200;

export default function TenantMemberList() {
  const { message, modal } = App.useApp();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);
  const rawTenantId = getCurrentUser()?.tenantId;
  const currentTenantId =
    typeof rawTenantId === 'number' ? rawTenantId : Number(rawTenantId) || undefined;

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
        showError(error, '角色列表加载失败');
      }
    }
  };

  const handleAssign = async (values: AssignValues) => {
    if (!assignRecord) {
      return;
    }
    try {
      await assignTenantMemberRoles(assignRecord.userId, values.roleIds ?? []);
      message.success('角色已更新');
      setAssignOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '分配角色失败');
      throw error;
    }
  };

  const handleBind = async (values: BindValues) => {
    if (!currentTenantId) {
      message.error('缺少租户上下文，请重新登录');
      return;
    }
    try {
      await bindUserTenant(values.userId, currentTenantId);
      message.success('成员已绑定');
      setBindOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '绑定用户失败');
      throw error;
    }
  };

  const handleUnbind = (row: TenantMemberVO) => {
    if (!currentTenantId) {
      message.error('缺少租户上下文，请重新登录');
      return;
    }
    modal.confirm({
      title: '移除成员',
      content: `确认将 ${row.username} 移出当前租户？该用户在本租户的角色将一并失效。`,
      okText: '移除',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await unbindUserTenant(row.userId, currentTenantId);
          message.success('成员已移除');
          tableRef.current?.reload();
        } catch (error) {
          showError(error, '移除成员失败');
        }
      },
    });
  };

  const columns: TableProps<TenantMemberVO>['columns'] = [
    { title: '用户ID', dataIndex: 'userId', width: 100 },
    { title: '用户名', dataIndex: 'username', width: 160 },
    {
      title: '昵称',
      dataIndex: 'nickname',
      width: 160,
      render: (value: string | undefined) => value || '-',
    },
    {
      title: '手机号',
      dataIndex: 'phone',
      width: 150,
      render: (value: string | undefined) => value || '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: number | undefined) =>
        value === 0 ? <Tag color="red">停用</Tag> : <Tag color="green">启用</Tag>,
    },
    {
      title: '本租户角色',
      dataIndex: 'roles',
      render: (value: TenantMemberVO['roles']) =>
        value.length === 0 ? (
          <span>未分配</span>
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
      title: '操作',
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
            分配角色
          </AuthButton>
          <AuthButton
            perm="sys:user:tenant"
            type="link"
            size="small"
            danger
            onClick={() => handleUnbind(row)}
          >
            移除
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
        title="租户成员"
        columns={columns}
        request={loadMembers}
        params={{ keyword }}
        onError={(error) => showError(error, '成员列表加载失败')}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: '关键字',
                element: <Input allowClear placeholder="用户名 / 昵称 / 手机号" />,
              },
            ]}
            onSearch={(values) => setKeyword((values.keyword as string) || undefined)}
            onReset={() => setKeyword(undefined)}
          />
        }
        toolbar={
          has('sys:user:tenant') ? (
            <AuthButton perm="sys:user:tenant" type="primary" onClick={() => setBindOpen(true)}>
              绑定用户
            </AuthButton>
          ) : null
        }
      />

      <FormModal<AssignValues>
        open={assignOpen}
        title={`分配角色：${assignRecord?.username ?? ''}`}
        form={assignForm}
        width={520}
        initialValues={{ roleIds: assignRecord?.roles.map((role) => role.id) }}
        onOk={handleAssign}
        onCancel={() => setAssignOpen(false)}
      >
        <Form.Item name="roleIds" label="角色">
          <Select
            mode="multiple"
            allowClear
            placeholder="仅可选择当前租户内的角色"
            options={roles.map((role) => ({
              value: role.id,
              label: `${role.roleName}（${role.roleCode}）`,
            }))}
          />
        </Form.Item>
      </FormModal>

      <FormModal<BindValues>
        open={bindOpen}
        title="绑定用户到当前租户"
        form={bindForm}
        width={480}
        onOk={handleBind}
        onCancel={() => setBindOpen(false)}
      >
        <Form.Item
          name="userId"
          label="用户 ID"
          rules={[{ required: true, message: '请输入用户 ID' }]}
        >
          <InputNumber min={1} style={{ width: '100%' }} placeholder="全局用户 ID" />
        </Form.Item>
      </FormModal>
    </>
  );
}
