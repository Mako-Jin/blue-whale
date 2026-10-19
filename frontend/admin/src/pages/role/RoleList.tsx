// 角色管理页 - 分页查询 / 新增 / 编辑 / 删除 / 分配权限
// 操作按钮均按权限码渲染（AuthButton / usePermission），无权限自动隐藏

import { App, Button, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag, type TableProps } from 'antd';
import { useEffect, useRef, useState } from 'react';
import {
  AuthButton,
  FormModal,
  ProTable,
  SearchForm,
  usePermission,
  type ProTableRef,
} from '@bluewhale/whale-ui';
import {
  assignRolePermissions,
  createRole,
  deleteRole,
  getRolePermissionIds,
  pageRoles,
  updateRole,
  type RoleVO,
} from '../../api/role';
import { pagePermissions, type PermissionVO } from '../../api/permission';

interface RoleFormValues {
  roleCode?: string;
  roleName: string;
  description?: string;
  sortNo?: number;
  status?: number;
}

type RoleQuery = { keyword?: string };

const STATUS_OPTIONS = [
  { value: 1, label: '启用' },
  { value: 0, label: '停用' },
];

export default function RoleList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);

  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editRecord, setEditRecord] = useState<RoleVO | null>(null);
  const [editorForm] = Form.useForm<RoleFormValues>();

  const [permOpen, setPermOpen] = useState(false);
  const [permRecord, setPermRecord] = useState<RoleVO | null>(null);
  const [permSaving, setPermSaving] = useState(false);
  const [permLoading, setPermLoading] = useState(false);
  const [allPermissions, setAllPermissions] = useState<PermissionVO[]>([]);
  const [checkedPermIds, setCheckedPermIds] = useState<number[]>([]);

  useEffect(() => {
    pagePermissions({ current: 1, size: 200 })
      .then((result) => setAllPermissions(result.records))
      .catch(() => setAllPermissions([]));
  }, []);

  const showError = (error: unknown, fallback: string) => {
    message.error(error instanceof Error ? error.message : fallback);
  };

  const handleDelete = async (row: RoleVO) => {
    try {
      await deleteRole(row.id);
      message.success('角色已删除');
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '删除失败');
    }
  };

  const handleSubmit = async (values: RoleFormValues) => {
    try {
      if (editRecord) {
        await updateRole(editRecord.id, {
          roleName: values.roleName,
          description: values.description,
          sortNo: values.sortNo,
          status: values.status,
        });
        message.success('角色已更新');
      } else {
        await createRole({
          roleCode: values.roleCode ?? '',
          roleName: values.roleName,
          description: values.description,
          sortNo: values.sortNo,
          status: values.status,
        });
        message.success('角色已创建');
      }
      setEditorOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, editRecord ? '更新失败' : '创建失败');
      throw error;
    }
  };

  const openAssignPermissions = async (row: RoleVO) => {
    setPermRecord(row);
    setCheckedPermIds([]);
    setPermOpen(true);
    setPermLoading(true);
    try {
      setCheckedPermIds(await getRolePermissionIds(row.id));
    } catch (error) {
      showError(error, '读取已分配权限失败');
    } finally {
      setPermLoading(false);
    }
  };

  const handleAssignPermissions = async () => {
    if (!permRecord) {
      return;
    }
    setPermSaving(true);
    try {
      await assignRolePermissions(permRecord.id, checkedPermIds);
      message.success('权限已分配');
      setPermOpen(false);
    } catch (error) {
      showError(error, '分配权限失败');
    } finally {
      setPermSaving(false);
    }
  };

  const columns: TableProps<RoleVO>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '角色编码', dataIndex: 'roleCode', width: 180 },
    { title: '角色名称', dataIndex: 'roleName', width: 160 },
    {
      title: '描述',
      dataIndex: 'description',
      render: (value: string | undefined) => value ?? '-',
    },
    { title: '排序', dataIndex: 'sortNo', width: 80 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: number | undefined) =>
        value === 1 ? <Tag color="green">启用</Tag> : <Tag color="red">停用</Tag>,
    },
    {
      title: '操作',
      key: 'action',
      width: 260,
      render: (_, row) => (
        <Space size={4} wrap>
          <AuthButton
            perm="sys:role:edit"
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
            perm="sys:role:assign"
            type="link"
            size="small"
            onClick={() => void openAssignPermissions(row)}
          >
            分配权限
          </AuthButton>
          {has('sys:role:delete') && (
            <Popconfirm title="确认删除该角色？" onConfirm={() => void handleDelete(row)}>
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
      <ProTable<RoleVO, RoleQuery>
        ref={tableRef}
        rowKey="id"
        title="角色列表"
        columns={columns}
        request={pageRoles}
        params={{ keyword }}
        onError={(error) => showError(error, '角色列表加载失败')}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: '关键字',
                element: <Input allowClear placeholder="角色编码 / 名称" />,
              },
            ]}
            onSearch={(values) => setKeyword((values.keyword as string) || undefined)}
            onReset={() => setKeyword(undefined)}
          />
        }
        toolbar={
          <AuthButton
            perm="sys:role:add"
            type="primary"
            onClick={() => {
              setEditRecord(null);
              setEditorOpen(true);
            }}
          >
            新增角色
          </AuthButton>
        }
      />

      <FormModal<RoleFormValues>
        open={editorOpen}
        title={editRecord ? `编辑角色：${editRecord.roleName}` : '新增角色'}
        form={editorForm}
        initialValues={editRecord ?? undefined}
        onOk={handleSubmit}
        onCancel={() => setEditorOpen(false)}
      >
        <Form.Item
          name="roleCode"
          label="角色编码"
          rules={editRecord ? [] : [{ required: true, message: '请输入角色编码' }]}
        >
          <Input placeholder="如 ROLE_AUDITOR" disabled={Boolean(editRecord)} />
        </Form.Item>
        <Form.Item name="roleName" label="角色名称" rules={[{ required: true, message: '请输入角色名称' }]}>
          <Input />
        </Form.Item>
        <Form.Item name="description" label="描述">
          <Input.TextArea rows={2} />
        </Form.Item>
        <Form.Item name="sortNo" label="排序" initialValue={1}>
          <InputNumber min={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="status" label="状态" initialValue={1}>
          <Select options={STATUS_OPTIONS} />
        </Form.Item>
      </FormModal>

      <Modal
        open={permOpen}
        title={`分配权限：${permRecord?.roleName ?? ''}`}
        width={640}
        onOk={() => void handleAssignPermissions()}
        onCancel={() => setPermOpen(false)}
        confirmLoading={permSaving}
      >
        <Table<PermissionVO>
          rowKey="id"
          size="small"
          loading={permLoading}
          dataSource={allPermissions}
          pagination={false}
          scroll={{ y: 360 }}
          rowSelection={{
            selectedRowKeys: checkedPermIds,
            onChange: (keys) => setCheckedPermIds(keys as number[]),
          }}
          columns={[
            { title: '权限编码', dataIndex: 'permCode', width: 200 },
            { title: '权限名称', dataIndex: 'permName' },
            { title: '类型', dataIndex: 'permType', width: 80 },
          ]}
        />
        <div className="admin-hint">已选 {checkedPermIds.length} / {allPermissions.length}</div>
      </Modal>
    </>
  );
}
