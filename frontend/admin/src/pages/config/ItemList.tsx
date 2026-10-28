// 业务配置管理页 - 列表（分页 / 新增 / 编辑 / 删除）+ 版本历史/回滚抽屉
// 操作按钮均按权限码渲染（AuthButton / usePermission），无权限自动隐藏
// 权限码：sys:config:list(246) / add(247) / edit(248) / delete(249) / rollback(250)

import {
  App,
  Button,
  Drawer,
  Form,
  Input,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  type TableProps,
} from 'antd';
import { useRef, useState } from 'react';
import {
  AuthButton,
  FormModal,
  ProTable,
  SearchForm,
  usePermission,
  type ProTableRef,
} from '@bluewhale/whale-ui';
import {
  createConfigItem,
  deleteConfigItem,
  listConfigItemHistory,
  pageConfigItems,
  rollbackConfigItem,
  updateConfigItem,
  type ConfigItemHistoryVO,
  type ConfigItemVO,
} from '../../api/config';

interface ConfigFormValues {
  configKey: string;
  configValue?: string;
  valueType?: string;
  description?: string;
  status?: number;
}

type ConfigQuery = { keyword?: string; status?: number; valueType?: string };

const TYPE_OPTIONS = [
  { value: 'STRING', label: '字符串 STRING' },
  { value: 'NUMBER', label: '数字 NUMBER' },
  { value: 'BOOLEAN', label: '布尔 BOOLEAN' },
  { value: 'JSON', label: 'JSON' },
];

const STATUS_OPTIONS = [
  { value: 1, label: '启用' },
  { value: 0, label: '停用' },
];

const TYPE_COLORS: Record<string, string> = {
  STRING: 'blue',
  NUMBER: 'cyan',
  BOOLEAN: 'purple',
  JSON: 'geekblue',
};

const OPERATION_TAGS: Record<string, { color: string; label: string }> = {
  CREATE: { color: 'green', label: '创建' },
  UPDATE: { color: 'blue', label: '更新' },
  ROLLBACK: { color: 'orange', label: '回滚' },
  DELETE: { color: 'red', label: '删除' },
};

function renderType(valueType: string | undefined) {
  if (!valueType) return '-';
  return <Tag color={TYPE_COLORS[valueType] ?? 'default'}>{valueType}</Tag>;
}

function renderStatus(status: number | undefined) {
  return status === 0 ? <Tag color="default">停用</Tag> : <Tag color="success">启用</Tag>;
}

function renderOperation(operation: string | undefined) {
  if (!operation) return '-';
  const item = OPERATION_TAGS[operation] ?? { color: 'default', label: operation };
  return <Tag color={item.color}>{item.label}</Tag>;
}

export default function ItemList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);

  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [status, setStatus] = useState<number | undefined>(undefined);
  const [valueType, setValueType] = useState<string | undefined>(undefined);

  const [editing, setEditing] = useState<ConfigItemVO | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [formInitial, setFormInitial] = useState<Partial<ConfigFormValues> | undefined>(undefined);
  const [form] = Form.useForm<ConfigFormValues>();

  const [historyOpen, setHistoryOpen] = useState(false);
  const [historyRecord, setHistoryRecord] = useState<ConfigItemVO | null>(null);
  const [history, setHistory] = useState<ConfigItemHistoryVO[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [rollbackTarget, setRollbackTarget] = useState<number | null>(null);

  const showError = (error: unknown, fallback: string) => {
    message.error(error instanceof Error ? error.message : fallback);
  };

  const refreshList = () => tableRef.current?.reload();

  const openCreate = () => {
    setEditing(null);
    setFormInitial({ configKey: '', configValue: '', valueType: 'STRING', status: 1 });
    setFormOpen(true);
  };

  const openEdit = (record: ConfigItemVO) => {
    setEditing(record);
    setFormInitial({
      configKey: record.configKey,
      configValue: record.configValue,
      valueType: record.valueType,
      description: record.description,
      status: record.status ?? 1,
    });
    setFormOpen(true);
  };

  const handleSave = async (values: ConfigFormValues) => {
    try {
      if (editing) {
        await updateConfigItem(editing.id, {
          configValue: values.configValue,
          valueType: values.valueType,
          description: values.description,
          status: values.status,
        });
        message.success('配置已更新');
      } else {
        await createConfigItem({
          configKey: values.configKey,
          configValue: values.configValue,
          valueType: values.valueType,
          description: values.description,
          status: values.status,
        });
        message.success('配置已创建');
      }
      setFormOpen(false);
      refreshList();
    } catch (error) {
      showError(error, '保存失败');
      throw error;
    }
  };

  const handleDelete = async (row: ConfigItemVO) => {
    try {
      await deleteConfigItem(row.id);
      message.success('配置已删除');
      refreshList();
    } catch (error) {
      showError(error, '删除失败');
    }
  };

  const openHistory = (record: ConfigItemVO) => {
    setHistoryRecord(record);
    setHistory([]);
    setHistoryOpen(true);
    setHistoryLoading(true);
    listConfigItemHistory(record.id)
      .then(setHistory)
      .catch((error) => showError(error, '历史加载失败'))
      .finally(() => setHistoryLoading(false));
  };

  const handleRollback = async (targetVersion: number) => {
    if (!historyRecord) {
      return;
    }
    setRollbackTarget(targetVersion);
    try {
      const updated = await rollbackConfigItem(historyRecord.id, targetVersion);
      message.success(`已回滚到版本 ${targetVersion}，当前版本 ${updated.version}`);
      setHistoryRecord(updated);
      setHistory(await listConfigItemHistory(updated.id));
      refreshList();
    } catch (error) {
      showError(error, '回滚失败');
    } finally {
      setRollbackTarget(null);
    }
  };

  const columns: TableProps<ConfigItemVO>['columns'] = [
    { title: '配置键', dataIndex: 'configKey', width: 220, ellipsis: true },
    {
      title: '配置值',
      dataIndex: 'configValue',
      ellipsis: true,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '类型',
      dataIndex: 'valueType',
      width: 100,
      render: (value: string | undefined) => renderType(value),
    },
    { title: '版本', dataIndex: 'version', width: 70, align: 'center' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (value: number | undefined) => renderStatus(value),
    },
    {
      title: '说明',
      dataIndex: 'description',
      width: 180,
      ellipsis: true,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 190,
      render: (_, row) => (
        <Space size={4} wrap>
          <Button type="link" size="small" onClick={() => openHistory(row)}>
            历史
          </Button>
          {has('sys:config:edit') && (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              编辑
            </Button>
          )}
          {has('sys:config:delete') && (
            <Popconfirm title="确认删除该配置项？删除后键可重建">
              <Button type="link" size="small" danger onClick={() => void handleDelete(row)}>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  const historyColumns: TableProps<ConfigItemHistoryVO>['columns'] = [
    { title: '版本', dataIndex: 'version', width: 60, align: 'center' },
    {
      title: '操作',
      dataIndex: 'operation',
      width: 80,
      render: (value: string | undefined) => renderOperation(value),
    },
    {
      title: '变更',
      key: 'change',
      ellipsis: true,
      render: (_, row) => {
        const oldText = row.oldValue ?? '∅';
        const newText = row.newValue ?? '∅';
        return (
          <span title={`${oldText} → ${newText}`}>
            {oldText} → {newText}
          </span>
        );
      },
    },
    {
      title: '操作人',
      dataIndex: 'operator',
      width: 90,
      render: (value: string | null | undefined) => value ?? '-',
    },
    {
      title: '时间',
      dataIndex: 'createTime',
      width: 170,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '回滚',
      key: 'rollback',
      width: 80,
      render: (_, row) => {
        const currentVersion = historyRecord?.version;
        const isCurrent = currentVersion != null && row.version === currentVersion;
        const canRollback =
          has('sys:config:rollback') && row.newValue != null && row.newValue !== '' && !isCurrent;
        if (isCurrent) {
          return <Tag color="success">当前</Tag>;
        }
        if (!canRollback) {
          return '-';
        }
        return (
          <Popconfirm
            title={`回滚到版本 ${row.version}？`}
            description="将恢复该版本的取值，版本号继续 +1"
            onConfirm={() => void handleRollback(row.version)}
          >
            <Button type="link" size="small" loading={rollbackTarget === row.version}>
              回滚
            </Button>
          </Popconfirm>
        );
      },
    },
  ];

  return (
    <>
      <ProTable<ConfigItemVO, ConfigQuery>
        ref={tableRef}
        rowKey="id"
        title="业务配置项"
        columns={columns}
        request={pageConfigItems}
        params={{ keyword, status, valueType }}
        onError={(error) => showError(error, '配置项列表加载失败')}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: '关键字',
                element: <Input allowClear placeholder="配置键 / 说明" />,
              },
              {
                name: 'valueType',
                label: '值类型',
                element: (
                  <Select allowClear options={TYPE_OPTIONS} placeholder="全部" style={{ width: 160 }} />
                ),
              },
              {
                name: 'status',
                label: '状态',
                element: (
                  <Select allowClear options={STATUS_OPTIONS} placeholder="全部" style={{ width: 120 }} />
                ),
              },
            ]}
            onSearch={(values) => {
              setKeyword((values.keyword as string) || undefined);
              setValueType((values.valueType as string) || undefined);
              const statusValue = values.status as number | undefined;
              setStatus(statusValue ?? undefined);
            }}
            onReset={() => {
              setKeyword(undefined);
              setValueType(undefined);
              setStatus(undefined);
            }}
          />
        }
        toolbar={
          <AuthButton perm="sys:config:add" type="primary" onClick={openCreate}>
            新增配置
          </AuthButton>
        }
      />

      <FormModal<ConfigFormValues>
        open={formOpen}
        title={editing ? `编辑配置：${editing.configKey}` : '新增配置'}
        form={form}
        initialValues={formInitial}
        onOk={handleSave}
        onCancel={() => setFormOpen(false)}
      >
        <Form.Item
          name="configKey"
          label="配置键"
          rules={[{ required: true, message: '请输入配置键' }]}
          extra={editing ? '配置键创建后不可修改' : '租户内唯一，如 order.pay.timeout'}
        >
          <Input
            placeholder="字母/数字开头，可含 . _ -"
            maxLength={128}
            disabled={Boolean(editing)}
          />
        </Form.Item>
        <Form.Item
          name="configValue"
          label="配置值"
          extra="按值类型校验字面量；JSON 存原文"
        >
          <Input.TextArea rows={3} placeholder="配置值" />
        </Form.Item>
        <Form.Item
          name="valueType"
          label="值类型"
          initialValue="STRING"
          rules={[{ required: true, message: '请选择值类型' }]}
        >
          <Select options={TYPE_OPTIONS} />
        </Form.Item>
        <Form.Item name="description" label="说明">
          <Input.TextArea rows={2} maxLength={255} placeholder="配置用途说明" />
        </Form.Item>
        <Form.Item
          name="status"
          label="状态"
          initialValue={1}
          rules={[{ required: true, message: '请选择状态' }]}
        >
          <Select options={STATUS_OPTIONS} />
        </Form.Item>
      </FormModal>

      <Drawer
        title={historyRecord ? `变更历史：${historyRecord.configKey}` : '变更历史'}
        width={760}
        open={historyOpen}
        onClose={() => setHistoryOpen(false)}
      >
        {historyRecord && (
          <>
            <Table<ConfigItemHistoryVO>
              rowKey="id"
              size="small"
              columns={historyColumns}
              dataSource={history}
              loading={historyLoading}
              pagination={false}
            />
            <div style={{ marginTop: 12, color: '#999' }}>
              当前版本：v{historyRecord.version}，值：{historyRecord.configValue ?? '∅'}（回滚产生新版本，不覆盖历史）
            </div>
          </>
        )}
      </Drawer>
    </>
  );
}
