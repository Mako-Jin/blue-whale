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
import { useI18n } from '../../i18n';

interface ConfigFormValues {
  configKey: string;
  configValue?: string;
  valueType?: string;
  description?: string;
  status?: number;
}

type ConfigQuery = { keyword?: string; status?: number; valueType?: string };

const TYPE_COLORS: Record<string, string> = {
  STRING: 'blue',
  NUMBER: 'cyan',
  BOOLEAN: 'purple',
  JSON: 'geekblue',
};

function renderType(valueType: string | undefined) {
  if (!valueType) return '-';
  return <Tag color={TYPE_COLORS[valueType] ?? 'default'}>{valueType}</Tag>;
}

export default function ItemList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const { t } = useI18n();
  const tableRef = useRef<ProTableRef>(null);

  const typeOptions = [
    { value: 'STRING', label: t('config.type.string') },
    { value: 'NUMBER', label: t('config.type.number') },
    { value: 'BOOLEAN', label: t('config.type.boolean') },
    { value: 'JSON', label: t('config.type.json') },
  ];

  const statusOptions = [
    { value: 1, label: t('config.status.enabled') },
    { value: 0, label: t('config.status.disabled') },
  ];

  const operationTags: Record<string, { color: string; label: string }> = {
    CREATE: { color: 'green', label: t('config.operation.create') },
    UPDATE: { color: 'blue', label: t('config.operation.update') },
    ROLLBACK: { color: 'orange', label: t('config.operation.rollback') },
    DELETE: { color: 'red', label: t('config.operation.delete') },
  };

  const renderStatus = (status: number | undefined) =>
    status === 0 ? (
      <Tag color="default">{t('config.status.disabled')}</Tag>
    ) : (
      <Tag color="success">{t('config.status.enabled')}</Tag>
    );

  const renderOperation = (operation: string | undefined) => {
    if (!operation) return '-';
    const item = operationTags[operation] ?? { color: 'default', label: operation };
    return <Tag color={item.color}>{item.label}</Tag>;
  };

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
        message.success(t('config.msg.updated'));
      } else {
        await createConfigItem({
          configKey: values.configKey,
          configValue: values.configValue,
          valueType: values.valueType,
          description: values.description,
          status: values.status,
        });
        message.success(t('config.msg.created'));
      }
      setFormOpen(false);
      refreshList();
    } catch (error) {
      showError(error, t('config.msg.saveFailed'));
      throw error;
    }
  };

  const handleDelete = async (row: ConfigItemVO) => {
    try {
      await deleteConfigItem(row.id);
      message.success(t('config.msg.deleted'));
      refreshList();
    } catch (error) {
      showError(error, t('config.msg.deleteFailed'));
    }
  };

  const openHistory = (record: ConfigItemVO) => {
    setHistoryRecord(record);
    setHistory([]);
    setHistoryOpen(true);
    setHistoryLoading(true);
    listConfigItemHistory(record.id)
      .then(setHistory)
      .catch((error) => showError(error, t('config.msg.historyFailed')))
      .finally(() => setHistoryLoading(false));
  };

  const handleRollback = async (targetVersion: number) => {
    if (!historyRecord) {
      return;
    }
    setRollbackTarget(targetVersion);
    try {
      const updated = await rollbackConfigItem(historyRecord.id, targetVersion);
      message.success(
        t('config.msg.rolledBack', { version: targetVersion, current: updated.version }),
      );
      setHistoryRecord(updated);
      setHistory(await listConfigItemHistory(updated.id));
      refreshList();
    } catch (error) {
      showError(error, t('config.msg.rollbackFailed'));
    } finally {
      setRollbackTarget(null);
    }
  };

  const columns: TableProps<ConfigItemVO>['columns'] = [
    { title: t('config.col.key'), dataIndex: 'configKey', width: 220, ellipsis: true },
    {
      title: t('config.col.value'),
      dataIndex: 'configValue',
      ellipsis: true,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('config.col.type'),
      dataIndex: 'valueType',
      width: 100,
      render: (value: string | undefined) => renderType(value),
    },
    { title: t('config.col.version'), dataIndex: 'version', width: 70, align: 'center' },
    {
      title: t('config.col.status'),
      dataIndex: 'status',
      width: 80,
      render: (value: number | undefined) => renderStatus(value),
    },
    {
      title: t('config.col.description'),
      dataIndex: 'description',
      width: 180,
      ellipsis: true,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('config.col.updateTime'),
      dataIndex: 'updateTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('config.col.action'),
      key: 'action',
      width: 190,
      render: (_, row) => (
        <Space size={4} wrap>
          <Button type="link" size="small" onClick={() => openHistory(row)}>
            {t('config.action.history')}
          </Button>
          {has('sys:config:edit') && (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              {t('config.action.edit')}
            </Button>
          )}
          {has('sys:config:delete') && (
            <Popconfirm title={t('config.confirm.delete')}>
              <Button type="link" size="small" danger onClick={() => void handleDelete(row)}>
                {t('config.action.delete')}
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  const historyColumns: TableProps<ConfigItemHistoryVO>['columns'] = [
    { title: t('config.col.version'), dataIndex: 'version', width: 60, align: 'center' },
    {
      title: t('config.col.action'),
      dataIndex: 'operation',
      width: 80,
      render: (value: string | undefined) => renderOperation(value),
    },
    {
      title: t('config.history.col.change'),
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
      title: t('config.history.col.operator'),
      dataIndex: 'operator',
      width: 90,
      render: (value: string | null | undefined) => value ?? '-',
    },
    {
      title: t('config.history.col.time'),
      dataIndex: 'createTime',
      width: 170,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('config.action.rollback'),
      key: 'rollback',
      width: 80,
      render: (_, row) => {
        const currentVersion = historyRecord?.version;
        const isCurrent = currentVersion != null && row.version === currentVersion;
        const canRollback =
          has('sys:config:rollback') && row.newValue != null && row.newValue !== '' && !isCurrent;
        if (isCurrent) {
          return <Tag color="success">{t('config.history.currentTag')}</Tag>;
        }
        if (!canRollback) {
          return '-';
        }
        return (
          <Popconfirm
            title={t('config.history.rollbackConfirm', { version: row.version })}
            description={t('config.history.rollbackNote')}
            onConfirm={() => void handleRollback(row.version)}
          >
            <Button type="link" size="small" loading={rollbackTarget === row.version}>
              {t('config.action.rollback')}
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
        title={t('config.list.title')}
        columns={columns}
        request={pageConfigItems}
        params={{ keyword, status, valueType }}
        onError={(error) => showError(error, t('config.list.loadFailed'))}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: t('config.search.keyword'),
                element: <Input allowClear placeholder={t('config.search.keywordPlaceholder')} />,
              },
              {
                name: 'valueType',
                label: t('config.form.valueType'),
                element: (
                  <Select
                    allowClear
                    options={typeOptions}
                    placeholder={t('config.search.all')}
                    style={{ width: 160 }}
                  />
                ),
              },
              {
                name: 'status',
                label: t('config.col.status'),
                element: (
                  <Select
                    allowClear
                    options={statusOptions}
                    placeholder={t('config.search.all')}
                    style={{ width: 120 }}
                  />
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
            {t('config.btn.create')}
          </AuthButton>
        }
      />

      <FormModal<ConfigFormValues>
        open={formOpen}
        title={
          editing
            ? t('config.form.editTitle', { configKey: editing.configKey })
            : t('config.form.createTitle')
        }
        form={form}
        initialValues={formInitial}
        onOk={handleSave}
        onCancel={() => setFormOpen(false)}
      >
        <Form.Item
          name="configKey"
          label={t('config.form.key')}
          rules={[{ required: true, message: t('config.form.keyRequired') }]}
          extra={editing ? t('config.form.keyExtraEdit') : t('config.form.keyExtraCreate')}
        >
          <Input
            placeholder={t('config.form.keyPlaceholder')}
            maxLength={128}
            disabled={Boolean(editing)}
          />
        </Form.Item>
        <Form.Item
          name="configValue"
          label={t('config.form.value')}
          extra={t('config.form.valueExtra')}
        >
          <Input.TextArea rows={3} placeholder={t('config.form.valuePlaceholder')} />
        </Form.Item>
        <Form.Item
          name="valueType"
          label={t('config.form.valueType')}
          initialValue="STRING"
          rules={[{ required: true, message: t('config.form.valueTypeRequired') }]}
        >
          <Select options={typeOptions} />
        </Form.Item>
        <Form.Item name="description" label={t('config.form.description')}>
          <Input.TextArea
            rows={2}
            maxLength={255}
            placeholder={t('config.form.descriptionPlaceholder')}
          />
        </Form.Item>
        <Form.Item
          name="status"
          label={t('config.col.status')}
          initialValue={1}
          rules={[{ required: true, message: t('config.form.statusRequired') }]}
        >
          <Select options={statusOptions} />
        </Form.Item>
      </FormModal>

      <Drawer
        title={
          historyRecord
            ? t('config.history.titleWithKey', { configKey: historyRecord.configKey })
            : t('config.history.title')
        }
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
              {t('config.history.current', {
                version: historyRecord.version,
                value: historyRecord.configValue ?? '∅',
              })}{' '}
              {t('config.history.footerNote')}
            </div>
          </>
        )}
      </Drawer>
    </>
  );
}
