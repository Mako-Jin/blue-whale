// 权限策略管理页 - 列表（分页 / 新增 / 编辑 / 删除 / 设默认）
// 权限码：sys:perm:strategy:list(2400) / add(2401) / edit(2402) / delete(2403) / set-default(2404)

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
  createPermStrategy,
  deletePermStrategy,
  pagePermStrategies,
  setDefaultPermStrategy,
  updatePermStrategy,
  type PermStrategyVO,
} from '../../../api/permStrategy';

type StrategyQuery = { keyword?: string };

const TYPE_OPTIONS = [
  { value: 'GRANT', label: '附加授权 GRANT' },
  { value: 'DENY', label: '显式拒绝 DENY' },
];

const STATUS_OPTIONS = [
  { value: 1, label: '启用' },
  { value: 0, label: '停用' },
];

function renderStatus(status: number | undefined) {
  return status === 0 ? <Tag color="default">停用</Tag> : <Tag color="success">启用</Tag>;
}

interface StrategyFormValues {
  strategyCode: string;
  strategyName: string;
  strategyType: string;
  roleIds?: string[];
  resourceIds?: string[];
  conditionJson?: string;
  isDefault?: boolean;
  priority?: number;
  status?: number;
  description?: string;
}

export default function PermStrategyList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);

  const [keyword, setKeyword] = useState<string | undefined>(undefined);

  const [editing, setEditing] = useState<PermStrategyVO | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [formInitial, setFormInitial] = useState<Partial<StrategyFormValues> | undefined>(
    undefined,
  );
  const [form] = Form.useForm<StrategyFormValues>();

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
    setFormInitial({ strategyType: 'GRANT', status: 1, priority: 0, roleIds: [], resourceIds: [] });
    setFormOpen(true);
  };

  const openEdit = (record: PermStrategyVO) => {
    setEditing(record);
    setFormInitial({
      strategyCode: record.strategyCode,
      strategyName: record.strategyName,
      strategyType: record.strategyType,
      roleIds: record.roleIds ?? [],
      resourceIds: record.resourceIds ?? [],
      conditionJson: record.conditionJson,
      isDefault: record.defaultStrategy,
      priority: record.priority ?? 0,
      status: record.status ?? 1,
      description: record.description,
    });
    setFormOpen(true);
  };

  const handleSave = async (values: StrategyFormValues) => {
    const payload = {
      ...values,
      resourceIds: values.resourceIds ?? [],
    };
    try {
      if (editing) {
        await updatePermStrategy(editing.id, payload);
        message.success('权限策略已更新');
      } else {
        await createPermStrategy(payload);
        message.success('权限策略已创建');
      }
      setFormOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '保存失败');
      throw error;
    }
  };

  const handleDelete = async (row: PermStrategyVO) => {
    try {
      await deletePermStrategy(row.id);
      message.success('权限策略已删除');
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '删除失败');
    }
  };

  const handleSetDefault = async (row: PermStrategyVO) => {
    try {
      await setDefaultPermStrategy(row.id);
      message.success('已设为默认策略');
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '设置默认失败');
    }
  };

  const columns: TableProps<PermStrategyVO>['columns'] = [
    { title: '策略编码', dataIndex: 'strategyCode', width: 160, ellipsis: true },
    { title: '策略名称', dataIndex: 'strategyName', width: 160, ellipsis: true },
    {
      title: '类型',
      dataIndex: 'strategyType',
      width: 100,
      render: (value: string | undefined) =>
        value === 'DENY' ? <Tag color="red">拒绝</Tag> : <Tag color="green">附加授权</Tag>,
    },
    {
      title: '默认',
      dataIndex: 'defaultStrategy',
      width: 70,
      render: (value: boolean | undefined) => (value ? <Tag color="gold">默认</Tag> : '-'),
    },
    {
      title: '生效角色',
      dataIndex: 'roleIds',
      width: 120,
      render: (value: string[] | undefined) => (value?.length ? `${value.length} 个` : '-'),
    },
    { title: '优先级', dataIndex: 'priority', width: 80, align: 'center' },
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
      width: 200,
      render: (_, row) => (
        <Space size={4}>
          {has('sys:perm:strategy:set-default') && !row.defaultStrategy && (
            <Button type="link" size="small" onClick={() => void handleSetDefault(row)}>
              设默认
            </Button>
          )}
          {has('sys:perm:strategy:edit') && (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              编辑
            </Button>
          )}
          {has('sys:perm:strategy:delete') && (
            <Popconfirm title="确认删除该策略？" onConfirm={() => void handleDelete(row)}>
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
      <ProTable<PermStrategyVO, StrategyQuery>
        ref={tableRef}
        rowKey="id"
        title="权限策略"
        columns={columns}
        request={pagePermStrategies}
        params={{ keyword }}
        onError={(error) => showError(error, '权限策略加载失败')}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: '关键字',
                element: <Input allowClear placeholder="策略编码 / 名称 / 说明" />,
              },
            ]}
            onSearch={(values) => setKeyword((values.keyword as string) || undefined)}
            onReset={() => setKeyword(undefined)}
          />
        }
        toolbar={
          <AuthButton perm="sys:perm:strategy:add" type="primary" onClick={openCreate}>
            新增策略
          </AuthButton>
        }
      />

      <FormModal<StrategyFormValues>
        open={formOpen}
        title={editing ? `编辑策略：${editing.strategyName}` : '新增策略'}
        form={form}
        initialValues={formInitial}
        width={640}
        onOk={handleSave}
        onCancel={() => setFormOpen(false)}
      >
        <Space style={{ display: 'flex' }}>
          <Form.Item
            name="strategyCode"
            label="策略编码"
            rules={[{ required: true, message: '请输入策略编码' }]}
          >
            <Input maxLength={64} disabled={Boolean(editing)} style={{ width: 200 }} />
          </Form.Item>
          <Form.Item
            name="strategyName"
            label="策略名称"
            rules={[{ required: true, message: '请输入策略名称' }]}
          >
            <Input maxLength={64} style={{ width: 200 }} />
          </Form.Item>
        </Space>
        <Space style={{ display: 'flex' }}>
          <Form.Item
            name="strategyType"
            label="类型"
            initialValue="GRANT"
            rules={[{ required: true, message: '请选择类型' }]}
          >
            <Select options={TYPE_OPTIONS} style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="status" label="状态" initialValue={1}>
            <Select options={STATUS_OPTIONS} style={{ width: 120 }} />
          </Form.Item>
          <Form.Item name="priority" label="优先级" initialValue={0}>
            <InputNumber min={0} style={{ width: 100 }} />
          </Form.Item>
        </Space>
        <Form.Item name="roleIds" label="生效角色" extra="策略只对该组角色生效；留空表示全角色">
          <Select
            mode="multiple"
            allowClear
            options={roleOptions}
            placeholder="选择角色"
            style={{ minHeight: 80 }}
          />
        </Form.Item>
        <Form.Item
          name="resourceIds"
          label="资源 ID"
          extra="逗号分隔的资源 ID（权限点/菜单等），留空表示全部资源"
        >
          <Select
            mode="tags"
            allowClear
            open={false}
            placeholder="输入资源 ID 后回车"
            style={{ minHeight: 60 }}
          />
        </Form.Item>
        <Form.Item
          name="conditionJson"
          label="附加条件 JSON"
          extra="按资源下发的附加过滤条件，如数据范围表达式"
        >
          <Input.TextArea rows={3} placeholder='{"deptScope":"SELF"}' />
        </Form.Item>
        <Form.Item
          name="isDefault"
          label="设为默认"
          valuePropName="checked"
          extra="命中该组角色时默认应用本策略"
        >
          <Select
            options={[
              { value: true, label: '是' },
              { value: false, label: '否' },
            ]}
            style={{ width: 120 }}
          />
        </Form.Item>
        <Form.Item name="description" label="说明">
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
      </FormModal>
    </>
  );
}
