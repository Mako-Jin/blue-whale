// 用户管理页 - 分页查询 / 新增 / 编辑 / 删除 / 启停 / 重置密码 / 分配角色
// 操作按钮均按权限码渲染（AuthButton / usePermission），无权限自动隐藏

import {
  Alert,
  App,
  Button,
  Checkbox,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Tag,
  Typography,
  type TableProps,
} from 'antd';
import { useEffect, useRef, useState } from 'react';
import {
  AuthButton,
  FormModal,
  ProTable,
  SearchForm,
  usePermission,
  type ProTableRef,
} from '@bluewhale/whale-ui';
import { treeDepts, type DeptVO } from '../../api/dept';
import { pageRoles, type RoleVO } from '../../api/role';
import {
  assignUserRoles,
  changeUserStatus,
  createUser,
  deleteUser,
  getUserRoleIds,
  pageUsers,
  resetUserPassword,
  updateUser,
  type ResetPasswordVO,
  type UserVO,
} from '../../api/user';

interface UserFormValues {
  username?: string;
  password?: string;
  nickname?: string;
  phone?: string;
  email?: string;
  /** 后端 Long 序列化为字符串 */
  deptId?: string;
  status?: number;
}

type UserQuery = { keyword?: string };

const STATUS_OPTIONS = [
  { value: 1, label: '启用' },
  { value: 0, label: '停用' },
];

function flattenDepts(nodes: DeptVO[], acc: DeptVO[] = []): DeptVO[] {
  nodes.forEach((node) => {
    acc.push(node);
    if (node.children?.length) {
      flattenDepts(node.children, acc);
    }
  });
  return acc;
}

export default function UserList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);

  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [depts, setDepts] = useState<DeptVO[]>([]);
  const [roles, setRoles] = useState<RoleVO[]>([]);

  // 新增/编辑
  const [editorOpen, setEditorOpen] = useState(false);
  const [editRecord, setEditRecord] = useState<UserVO | null>(null);
  const [editorForm] = Form.useForm<UserFormValues>();

  // 重置密码：确认后由后端生成一次性临时口令，仅在结果弹窗展示一次
  const [resetOpen, setResetOpen] = useState(false);
  const [resetRecord, setResetRecord] = useState<UserVO | null>(null);
  const [resetLoading, setResetLoading] = useState(false);
  const [resetResult, setResetResult] = useState<ResetPasswordVO | null>(null);

  // 分配角色
  const [assignOpen, setAssignOpen] = useState(false);
  const [assignRecord, setAssignRecord] = useState<UserVO | null>(null);
  const [assignSaving, setAssignSaving] = useState(false);
  const [checkedRoleIds, setCheckedRoleIds] = useState<string[]>([]);

  useEffect(() => {
    treeDepts()
      .then(setDepts)
      .catch(() => setDepts([]));
    pageRoles({ current: 1, size: 200 })
      .then((result) => setRoles(result.records))
      .catch(() => setRoles([]));
  }, []);

  const showError = (error: unknown, fallback: string) => {
    message.error(error instanceof Error ? error.message : fallback);
  };

  const deptOptions = flattenDepts(depts).map((dept) => ({ value: dept.id, label: dept.deptName }));

  const handleToggleStatus = async (row: UserVO, status: number) => {
    try {
      await changeUserStatus(row.id, status);
      message.success(status === 1 ? '用户已启用' : '用户已停用');
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '状态变更失败');
    }
  };

  const handleDelete = async (row: UserVO) => {
    try {
      await deleteUser(row.id);
      message.success('用户已删除');
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '删除失败');
    }
  };

  const handleSubmit = async (values: UserFormValues) => {
    try {
      if (editRecord) {
        await updateUser(editRecord.id, {
          password: values.password || undefined,
          nickname: values.nickname,
          phone: values.phone,
          email: values.email,
          deptId: values.deptId,
          status: values.status,
        });
        message.success('用户已更新');
      } else {
        await createUser({
          username: values.username ?? '',
          password: values.password ?? '',
          nickname: values.nickname,
          phone: values.phone,
          email: values.email,
          deptId: values.deptId,
          status: values.status,
        });
        message.success('用户已创建');
      }
      setEditorOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, editRecord ? '更新失败' : '创建失败');
      throw error;
    }
  };

  const handleResetPassword = async () => {
    if (!resetRecord) {
      return;
    }
    setResetLoading(true);
    try {
      const result = await resetUserPassword(resetRecord.id);
      setResetOpen(false);
      setResetResult(result);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '重置密码失败');
    } finally {
      setResetLoading(false);
    }
  };

  const openAssignRoles = async (row: UserVO) => {
    setAssignRecord(row);
    setCheckedRoleIds([]);
    setAssignOpen(true);
    try {
      setCheckedRoleIds(await getUserRoleIds(row.id));
    } catch (error) {
      showError(error, '读取已分配角色失败');
    }
  };

  const handleAssignRoles = async () => {
    if (!assignRecord) {
      return;
    }
    setAssignSaving(true);
    try {
      await assignUserRoles(assignRecord.id, checkedRoleIds);
      message.success('角色已分配');
      setAssignOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '分配角色失败');
    } finally {
      setAssignSaving(false);
    }
  };

  const columns: TableProps<UserVO>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '用户名', dataIndex: 'username', width: 140 },
    {
      title: '昵称',
      dataIndex: 'nickname',
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '手机号',
      dataIndex: 'phone',
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '邮箱',
      dataIndex: 'email',
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '部门',
      dataIndex: 'deptId',
      width: 140,
      render: (value: number | undefined) =>
        deptOptions.find((option) => option.value === value)?.label ?? '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: number | undefined, row) => (
        <Switch
          checked={value === 1}
          disabled={!has('sys:user:status')}
          onChange={(checked) => void handleToggleStatus(row, checked ? 1 : 0)}
        />
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 300,
      render: (_, row) => (
        <Space size={4} wrap>
          <AuthButton
            perm="sys:user:edit"
            type="link"
            size="small"
            onClick={() => {
              setEditRecord(row);
              setEditorOpen(true);
            }}
          >
            编辑
          </AuthButton>
          <AuthButton
            perm="sys:user:resetpwd"
            type="link"
            size="small"
            onClick={() => {
              setResetRecord(row);
              setResetOpen(true);
            }}
          >
            重置密码
          </AuthButton>
          <AuthButton
            perm="sys:user:assign"
            type="link"
            size="small"
            onClick={() => void openAssignRoles(row)}
          >
            分配角色
          </AuthButton>
          {has('sys:user:delete') && (
            <Popconfirm title="确认删除该用户？" onConfirm={() => void handleDelete(row)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <ProTable<UserVO, UserQuery>
        ref={tableRef}
        rowKey="id"
        title="用户列表"
        columns={columns}
        request={pageUsers}
        params={{ keyword }}
        onError={(error) => showError(error, '用户列表加载失败')}
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
          <AuthButton
            perm="sys:user:add"
            type="primary"
            onClick={() => {
              setEditRecord(null);
              setEditorOpen(true);
            }}
          >
            新增用户
          </AuthButton>
        }
      />

      <FormModal<UserFormValues>
        open={editorOpen}
        title={editRecord ? `编辑用户：${editRecord.username}` : '新增用户'}
        form={editorForm}
        initialValues={editRecord ?? undefined}
        onOk={handleSubmit}
        onCancel={() => setEditorOpen(false)}
      >
        {!editRecord && (
          <Form.Item
            name="username"
            label="用户名"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input placeholder="登录账号" />
          </Form.Item>
        )}
        <Form.Item
          name="password"
          label={editRecord ? '密码（留空则不修改）' : '密码'}
          rules={
            editRecord
              ? [{ min: 12, message: '密码至少 12 位' }]
              : [
                  { required: true, message: '请输入密码' },
                  { min: 12, message: '密码至少 12 位' },
                ]
          }
        >
          <Input.Password placeholder={editRecord ? '留空表示不修改' : '至少 12 位，含大小写字母、数字和特殊字符'} />
        </Form.Item>
        <Form.Item name="nickname" label="昵称">
          <Input />
        </Form.Item>
        <Form.Item name="phone" label="手机号">
          <Input />
        </Form.Item>
        <Form.Item name="email" label="邮箱">
          <Input />
        </Form.Item>
        <Form.Item name="deptId" label="部门">
          <Select allowClear options={deptOptions} placeholder="请选择部门" />
        </Form.Item>
        <Form.Item name="status" label="状态" initialValue={1}>
          <Select options={STATUS_OPTIONS} />
        </Form.Item>
      </FormModal>

      <Modal
        open={resetOpen}
        title={`重置密码：${resetRecord?.username ?? ''}`}
        okText="确认重置"
        confirmLoading={resetLoading}
        onOk={() => void handleResetPassword()}
        onCancel={() => setResetOpen(false)}
      >
        <p style={{ margin: 0 }}>
          系统将生成一次性临时口令，仅在本次操作结果中展示一次；该账号下次登录时必须修改口令。
        </p>
      </Modal>

      <Modal
        open={resetResult !== null}
        title="一次性临时口令（仅展示一次）"
        footer={
          <Button type="primary" onClick={() => setResetResult(null)}>
            我已保存
          </Button>
        }
        onCancel={() => setResetResult(null)}
      >
        <Alert
          type="warning"
          showIcon
          message="请立即通过安全渠道告知用户，关闭本窗口后将无法再次查看该口令。"
        />
        <div style={{ marginTop: 16 }}>
          账号：<Typography.Text strong>{resetResult?.username}</Typography.Text>
        </div>
        <div style={{ marginTop: 8 }}>
          <Typography.Text copyable={{ text: resetResult?.temporaryPassword ?? '' }} code>
            {resetResult?.temporaryPassword}
          </Typography.Text>
        </div>
      </Modal>

      <Modal
        open={assignOpen}
        title={`分配角色：${assignRecord?.username ?? ''}`}
        onOk={() => void handleAssignRoles()}
        onCancel={() => setAssignOpen(false)}
        confirmLoading={assignSaving}
      >
        <Checkbox.Group
          value={checkedRoleIds}
          onChange={(values) => setCheckedRoleIds(values as string[])}
          style={{ display: 'flex', flexDirection: 'column', gap: 8 }}
        >
          {roles.map((role) => (
            <Checkbox key={role.id} value={role.id}>
              {role.roleName}
              <Tag style={{ marginLeft: 8 }}>{role.roleCode}</Tag>
            </Checkbox>
          ))}
        </Checkbox.Group>
      </Modal>
    </>
  );
}
