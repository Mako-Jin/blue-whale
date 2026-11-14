// 角色约束管理页 - 列表（分页 / 新增 / 编辑 / 删除）
// 权限码：sys:role:constraint:list(2405) / add(2406) / edit(2407) / delete(2408)
// MUTEX = 互斥（约束内角色不可同时持有）；CARDINALITY = 基数（最多持有 maxAssign 个）

import {
  App,
  Button,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Tag,
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
import { pageRoles, type RoleVO } from '../../../api/role';
import {
  createRoleConstraint,
  deleteRoleConstraint,
  pageRoleConstraints,
  updateRoleConstraint,
  type RoleConstraintVO,
} from '../../../api/roleConstraint';

type ConstraintQuery = { keyword?: string; constraintType?: string };

const TYPE_OPTIONS = [
  { value: 'MUTEX', label: '互斥 MUTEX' },
  { value: 'CARDINALITY', label: '基数 CARDINALITY' },
];

const STATUS_OPTIONS = [
  { value: 1, label: '启用' },
  { value: 0, label: '停用' },
];

function renderStatus(status: number | undefined) {
  return status === 0 ? <Tag color="default">停用</Tag> : <Tag color="success">启用</Tag>;
}

interface ConstraintFormValues {
  constraintCode: string;
  constraintName: string;
  constraintType: string;
  roleIds?: string[];
  maxAssign?: number;
  status?: number;
  description?: string;
}

export default function RoleConstraintList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);

  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [constraintType, setConstraintType] = useState<string | undefined>(undefined);

  const [editing, setEditing] = useState<RoleConstraintVO | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [formInitial, setFormInitial] = useState<Partial<ConstraintFormValues> | undefined>(
    undefined,
  );
  const [form] = Form.useForm<ConstraintFormValues>();

  const [roleOptions, setRoleOptions] = useState<{ value: string; label: string }[]>([]);

  const showError = (error: unknown, fallback: string) => {
    message.error(error instanceof Error ? error.message : fallback);
  };

  const loadRoles = () => {
    pageRoles({ current: 1, size: 100 })
      .then((page) => {
        setRoleOptions(
          page.records.map((role: RoleVO) => ({
            value: role.id,
            label: `${role.roleName}（${role.roleCode}）`,
          })),
        );
      })
      .catch((error) => showError(error, '角色列表加载失败'));
  };

  useEffect(() => {
    loadRoles();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 仅首挂载加载角色选项
  }, []);

  const openCreate = () => {
    setEditing(null);
    setFormInitial({ constraintType: 'MUTEX', status: 1, roleIds: [] });
    setFormOpen(true);
  };

  const openEdit = (record: RoleConstraintVO) => {
    setEditing(record);
    setFormInitial({
      constraintCode: record.constraintCode,
      constraintName: record.constraintName,
      constraintType: record.constraintType,
      roleIds: record.roleIds ?? [],
      maxAssign: record.maxAssign,
      status: record.status ?? 1,
      description: record.description,
    });
    setFormOpen(true);
  };

  const handleSave = async (values: ConstraintFormValues) => {
    try {
      if (editing) {
        await updateRoleConstraint(editing.id, values);
        message.success('角色约束已更新');
      } else {
        await createRoleConstraint(values);
        message.success('角色约束已创建');
      }
      setFormOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '保存失败');
      throw error;
    }
  };

  const handleDelete = async (row: RoleConstraintVO) => {
    try {
      await deleteRoleConstraint(row.id);
      message.success('角色约束已删除');
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '删除失败');
    }
  };

  const columns: TableProps<RoleConstraintVO>['columns'] = [
    { title: '约束编码', dataIndex: 'constraintCode', width: 160, ellipsis: true },
    { title: '约束名称', dataIndex: 'constraintName', width: 160, ellipsis: true },
    {
      title: '类型',
      dataIndex: 'constraintType',
      width: 130,
      render: (value: string | undefined) =>
        value === 'CARDINALITY' ? <Tag color="purple">基数</Tag> : <Tag color="volcano">互斥</Tag>,
    },
    {
      title: '约束角色',
      dataIndex: 'roleIds',
      width: 100,
      render: (value: string[] | undefined) => (value?.length ? `${value.length} 个` : '-'),
    },
    {
      title: '最大持有',
      dataIndex: 'maxAssign',
      width: 90,
      align: 'center',
      render: (value: number | undefined, row) =>
        row.constraintType === 'CARDINALITY' && value ? value : '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (value: number | undefined) => renderStatus(value),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 170,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_, row) => (
        <Space size={4}>
          {has('sys:role:constraint:edit') && (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              编辑
            </Button>
          )}
          {has('sys:role:constraint:delete') && (
            <Popconfirm title="确认删除该约束？" onConfirm={() => void handleDelete(row)}>
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
      <ProTable<RoleConstraintVO, ConstraintQuery>
        ref={tableRef}
        rowKey="id"
        title="角色约束"
        columns={columns}
        request={pageRoleConstraints}
        params={{ keyword, constraintType }}
        onError={(error) => showError(error, '角色约束加载失败')}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: '关键字',
                element: <Input allowClear placeholder="约束编码 / 名称 / 说明" />,
              },
              {
                name: 'constraintType',
                label: '类型',
                element: (
                  <Select
                    allowClear
                    options={TYPE_OPTIONS}
                    placeholder="全部"
                    style={{ width: 180 }}
                  />
                ),
              },
            ]}
            onSearch={(values) => {
              setKeyword((values.keyword as string) || undefined);
              setConstraintType((values.constraintType as string) || undefined);
            }}
            onReset={() => {
              setKeyword(undefined);
              setConstraintType(undefined);
            }}
          />
        }
        toolbar={
          <AuthButton perm="sys:role:constraint:add" type="primary" onClick={openCreate}>
            新增约束
          </AuthButton>
        }
      />

      <FormModal<ConstraintFormValues>
        open={formOpen}
        title={editing ? `编辑约束：${editing.constraintName}` : '新增约束'}
        form={form}
        initialValues={formInitial}
        width={640}
        onOk={handleSave}
        onCancel={() => setFormOpen(false)}
      >
        <Space style={{ display: 'flex' }}>
          <Form.Item
            name="constraintCode"
            label="约束编码"
            rules={[{ required: true, message: '请输入约束编码' }]}
          >
            <Input maxLength={64} disabled={Boolean(editing)} style={{ width: 200 }} />
          </Form.Item>
          <Form.Item
            name="constraintName"
            label="约束名称"
            rules={[{ required: true, message: '请输入约束名称' }]}
          >
            <Input maxLength={64} style={{ width: 200 }} />
          </Form.Item>
        </Space>
        <Space style={{ display: 'flex' }}>
          <Form.Item
            name="constraintType"
            label="类型"
            initialValue="MUTEX"
            rules={[{ required: true, message: '请选择类型' }]}
          >
            <Select options={TYPE_OPTIONS} style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="status" label="状态" initialValue={1}>
            <Select options={STATUS_OPTIONS} style={{ width: 120 }} />
          </Form.Item>
          <Form.Item
            noStyle
            shouldUpdate={(prev, next) => prev.constraintType !== next.constraintType}
          >
            {({ getFieldValue }) =>
              getFieldValue('constraintType') === 'CARDINALITY' ? (
                <Form.Item name="maxAssign" label="最大持有" extra="最多可同时持有的角色数">
                  <InputNumber min={1} style={{ width: 120 }} />
                </Form.Item>
              ) : null
            }
          </Form.Item>
        </Space>
        <Form.Item
          name="roleIds"
          label="约束角色"
          rules={[{ required: true, message: '请选择受约束的角色' }]}
          extra="MUTEX：约束内角色互斥不可同时持有；CARDINALITY：持有数量受限"
        >
          <Select
            mode="multiple"
            allowClear
            options={roleOptions}
            placeholder="选择角色"
            style={{ minHeight: 80 }}
          />
        </Form.Item>
        <Form.Item name="description" label="说明">
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
      </FormModal>
    </>
  );
}
