// AI 助手管理页 - 定义列表（分页 / 新增 / 编辑 / 发布 / 停用 / 删除）+ 工具授权抽屉
// 权限码：sys:assistant:define(2300)；按钮按权限码渲染（AuthButton / usePermission）

import {
  App,
  Button,
  Checkbox,
  Drawer,
  Form,
  Input,
  Popconfirm,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  type TableProps,
} from 'antd';
import { useCallback, useRef, useState } from 'react';
import { AuthButton, FormModal, ProTable, SearchForm, type ProTableRef } from '@bluewhale/whale-ui';
import {
  createDefinition,
  deleteDefinition,
  disableDefinition,
  listRegisteredTools,
  listToolWhitelist,
  pageDefinitions,
  publishDefinition,
  setToolWhitelist,
  updateDefinition,
  type DefinitionVO,
  type ToolInfoVO,
} from '../../api/assistant';
import { useI18n } from '../../i18n';

interface DefinitionFormValues {
  code: string;
  name?: string;
  description?: string;
  systemPrompt?: string;
  model?: string;
  temperature?: number;
  maxTokens?: number;
  knowledgeScope?: string;
}

type DefinitionQuery = { keyword?: string; status?: string };

const RISK_TAGS: Record<string, string> = { L0: 'green', L1: 'orange', L2: 'red' };

export default function DefinitionList() {
  const { message } = App.useApp();
  const { t } = useI18n();
  const tableRef = useRef<ProTableRef>(null);

  const statusOptions = [
    { value: 'draft', label: t('assistant.definition.status.draft') },
    { value: 'published', label: t('assistant.definition.status.published') },
    { value: 'disabled', label: t('assistant.definition.status.disabled') },
  ];

  const statusTags: Record<string, { color: string; label: string }> = {
    draft: { color: 'default', label: t('assistant.definition.status.draft') },
    published: { color: 'success', label: t('assistant.definition.status.published') },
    disabled: { color: 'warning', label: t('assistant.definition.status.disabled') },
  };

  const renderStatus = (status: string | undefined) => {
    if (!status) return '-';
    const item = statusTags[status] ?? { color: 'default', label: status };
    return <Tag color={item.color}>{item.label}</Tag>;
  };

  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [status, setStatus] = useState<string | undefined>(undefined);

  const [editing, setEditing] = useState<DefinitionVO | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [formInitial, setFormInitial] = useState<Partial<DefinitionFormValues> | undefined>(
    undefined,
  );
  const [form] = Form.useForm<DefinitionFormValues>();

  const [toolsOpen, setToolsOpen] = useState(false);
  const [toolsRecord, setToolsRecord] = useState<DefinitionVO | null>(null);
  const [tools, setTools] = useState<ToolInfoVO[]>([]);
  const [toolsLoading, setToolsLoading] = useState(false);
  const [granted, setGranted] = useState<string[]>([]);
  const [savingTools, setSavingTools] = useState(false);

  const showError = (error: unknown, fallback: string) => {
    message.error(error instanceof Error ? error.message : fallback);
  };

  const refreshList = useCallback(() => tableRef.current?.reload(), []);

  const openCreate = () => {
    setEditing(null);
    setFormInitial({ code: '', name: '', systemPrompt: '' });
    setFormOpen(true);
  };

  const openEdit = (record: DefinitionVO) => {
    setEditing(record);
    const params = (record.modelParams ?? {}) as Record<string, unknown>;
    setFormInitial({
      code: record.code,
      name: record.name,
      description: record.description,
      systemPrompt: record.systemPrompt,
      model: typeof params.model === 'string' ? params.model : undefined,
      temperature: typeof params.temperature === 'number' ? params.temperature : undefined,
      maxTokens: typeof params.max_tokens === 'number' ? params.max_tokens : undefined,
      knowledgeScope: record.knowledgeScope,
    });
    setFormOpen(true);
  };

  const handleSave = async (values: DefinitionFormValues) => {
    const modelParams: Record<string, unknown> = {};
    if (values.model) modelParams.model = values.model;
    if (values.temperature != null) modelParams.temperature = values.temperature;
    if (values.maxTokens != null) modelParams.max_tokens = values.maxTokens;
    const body = {
      code: values.code,
      name: values.name,
      description: values.description,
      systemPrompt: values.systemPrompt,
      modelParams: Object.keys(modelParams).length ? modelParams : undefined,
      knowledgeScope: values.knowledgeScope,
    };
    try {
      if (editing) {
        await updateDefinition(editing.id, body);
        message.success(t('assistant.definition.msg.updated'));
      } else {
        await createDefinition(body);
        message.success(t('assistant.definition.msg.created'));
      }
      setFormOpen(false);
      refreshList();
    } catch (error) {
      showError(error, t('assistant.definition.msg.saveFailed'));
      throw error;
    }
  };

  const handlePublish = async (row: DefinitionVO) => {
    try {
      await publishDefinition(row.id);
      message.success(t('assistant.definition.msg.published'));
      refreshList();
    } catch (error) {
      showError(error, t('assistant.definition.msg.publishFailed'));
    }
  };

  const handleDisable = async (row: DefinitionVO) => {
    try {
      await disableDefinition(row.id);
      message.success(t('assistant.definition.msg.disabled'));
      refreshList();
    } catch (error) {
      showError(error, t('assistant.definition.msg.disableFailed'));
    }
  };

  const handleDelete = async (row: DefinitionVO) => {
    try {
      await deleteDefinition(row.id);
      message.success(t('assistant.definition.msg.deleted'));
      refreshList();
    } catch (error) {
      showError(error, t('assistant.definition.msg.deleteFailed'));
    }
  };

  const openTools = (record: DefinitionVO) => {
    setToolsRecord(record);
    setTools([]);
    setGranted([]);
    setToolsOpen(true);
    setToolsLoading(true);
    Promise.all([listRegisteredTools(), listToolWhitelist(record.id)])
      .then(([registry, whitelist]) => {
        setTools(registry);
        setGranted(whitelist);
      })
      .catch((error) => showError(error, t('assistant.definition.msg.toolsLoadFailed')))
      .finally(() => setToolsLoading(false));
  };

  const handleSaveTools = async () => {
    if (!toolsRecord) {
      return;
    }
    setSavingTools(true);
    try {
      await setToolWhitelist(toolsRecord.id, granted);
      message.success(t('assistant.definition.msg.toolsSaved'));
      setToolsOpen(false);
      refreshList();
    } catch (error) {
      showError(error, t('assistant.definition.msg.saveFailed'));
    } finally {
      setSavingTools(false);
    }
  };

  const columns: TableProps<DefinitionVO>['columns'] = [
    {
      title: t('assistant.definition.column.code'),
      dataIndex: 'code',
      width: 160,
      ellipsis: true,
    },
    {
      title: t('assistant.definition.column.name'),
      dataIndex: 'name',
      width: 160,
      ellipsis: true,
      render: (v?: string) => v ?? '-',
    },
    {
      title: t('assistant.definition.column.description'),
      dataIndex: 'description',
      ellipsis: true,
      render: (v?: string) => v ?? '-',
    },
    {
      title: t('assistant.definition.column.tools'),
      key: 'tools',
      width: 150,
      render: (_, row) =>
        row.toolWhitelist.length ? (
          <Space size={2} wrap>
            {row.toolWhitelist.map((code) => (
              <Tag key={code}>{code}</Tag>
            ))}
          </Space>
        ) : (
          '-'
        ),
    },
    {
      title: t('assistant.definition.column.status'),
      dataIndex: 'status',
      width: 90,
      render: (v?: string) => renderStatus(v),
    },
    {
      title: t('assistant.definition.column.updateTime'),
      dataIndex: 'updateTime',
      width: 170,
      render: (v?: string) => v ?? '-',
    },
    {
      title: t('assistant.definition.column.action'),
      key: 'action',
      width: 250,
      render: (_, row) => (
        <Space size={4} wrap>
          <Button type="link" size="small" onClick={() => openTools(row)}>
            {t('assistant.definition.action.tools')}
          </Button>
          <Button type="link" size="small" onClick={() => openEdit(row)}>
            {t('assistant.definition.action.edit')}
          </Button>
          {row.status === 'draft' && (
            <Popconfirm title={t('assistant.definition.confirm.publish')}>
              <Button type="link" size="small" onClick={() => void handlePublish(row)}>
                {t('assistant.definition.action.publish')}
              </Button>
            </Popconfirm>
          )}
          {row.status === 'published' && (
            <Popconfirm title={t('assistant.definition.confirm.disable')}>
              <Button type="link" size="small" onClick={() => void handleDisable(row)}>
                {t('assistant.definition.action.disable')}
              </Button>
            </Popconfirm>
          )}
          <Popconfirm title={t('assistant.definition.confirm.delete')}>
            <Button type="link" size="small" danger onClick={() => void handleDelete(row)}>
              {t('assistant.definition.action.delete')}
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <>
      <ProTable<DefinitionVO, DefinitionQuery>
        ref={tableRef}
        rowKey="id"
        title={t('assistant.definition.title')}
        columns={columns}
        request={pageDefinitions}
        params={{ keyword, status }}
        onError={(error) => showError(error, t('assistant.definition.msg.loadFailed'))}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: t('assistant.definition.search.keyword'),
                element: (
                  <Input
                    allowClear
                    placeholder={t('assistant.definition.search.keywordPlaceholder')}
                  />
                ),
              },
              {
                name: 'status',
                label: t('assistant.definition.column.status'),
                element: (
                  <Select
                    allowClear
                    options={statusOptions}
                    placeholder={t('assistant.definition.search.all')}
                    style={{ width: 140 }}
                  />
                ),
              },
            ]}
            onSearch={(values) => {
              setKeyword((values.keyword as string) || undefined);
              setStatus((values.status as string) || undefined);
            }}
            onReset={() => {
              setKeyword(undefined);
              setStatus(undefined);
            }}
          />
        }
        toolbar={
          <AuthButton perm="sys:assistant:define" type="primary" onClick={openCreate}>
            {t('assistant.definition.create')}
          </AuthButton>
        }
      />

      <FormModal<DefinitionFormValues>
        open={formOpen}
        title={
          editing
            ? t('assistant.definition.editTitle', { code: editing.code })
            : t('assistant.definition.create')
        }
        form={form}
        initialValues={formInitial}
        onOk={handleSave}
        onCancel={() => setFormOpen(false)}
      >
        <Form.Item
          name="code"
          label={t('assistant.definition.field.code')}
          rules={[{ required: true, message: t('assistant.definition.field.codeRequired') }]}
          extra={
            editing
              ? t('assistant.definition.field.codeExtraEdit')
              : t('assistant.definition.field.codeExtraCreate')
          }
        >
          <Input
            placeholder={t('assistant.definition.field.codePlaceholder')}
            maxLength={64}
            disabled={Boolean(editing)}
          />
        </Form.Item>
        <Form.Item name="name" label={t('assistant.definition.field.name')}>
          <Input maxLength={64} placeholder={t('assistant.definition.field.namePlaceholder')} />
        </Form.Item>
        <Form.Item name="description" label={t('assistant.definition.field.description')}>
          <Input.TextArea
            rows={2}
            maxLength={255}
            placeholder={t('assistant.definition.field.descriptionPlaceholder')}
          />
        </Form.Item>
        <Form.Item
          name="systemPrompt"
          label={t('assistant.definition.field.systemPrompt')}
          extra={t('assistant.definition.field.systemPromptExtra')}
        >
          <Input.TextArea
            rows={4}
            maxLength={8000}
            placeholder={t('assistant.definition.field.systemPromptPlaceholder')}
          />
        </Form.Item>
        <Space size={12} wrap style={{ display: 'flex' }}>
          <Form.Item name="model" label={t('assistant.definition.field.model')}>
            <Input
              placeholder={t('assistant.definition.field.modelPlaceholder')}
              style={{ width: 180 }}
            />
          </Form.Item>
          <Form.Item name="temperature" label={t('assistant.definition.field.temperature')}>
            <Input type="number" min={0} max={2} step={0.1} style={{ width: 100 }} />
          </Form.Item>
          <Form.Item name="maxTokens" label="maxTokens">
            <Input type="number" min={1} style={{ width: 120 }} />
          </Form.Item>
        </Space>
        <Form.Item
          name="knowledgeScope"
          label={t('assistant.definition.field.knowledgeScope')}
          extra={t('assistant.definition.field.knowledgeScopeExtra')}
        >
          <Input maxLength={128} placeholder="doc,code,sql" />
        </Form.Item>
      </FormModal>

      <Drawer
        title={
          toolsRecord
            ? t('assistant.definition.tools.title', { code: toolsRecord.code })
            : t('assistant.definition.action.tools')
        }
        width={640}
        open={toolsOpen}
        onClose={() => setToolsOpen(false)}
        extra={
          <Button
            type="primary"
            size="small"
            loading={savingTools}
            onClick={() => void handleSaveTools()}
          >
            {t('assistant.definition.tools.save')}
          </Button>
        }
      >
        {toolsLoading ? (
          <Spin />
        ) : (
          <Table<ToolInfoVO>
            rowKey="name"
            size="small"
            dataSource={tools}
            pagination={false}
            columns={[
              {
                title: '',
                key: 'pick',
                width: 40,
                render: (_, row) => (
                  <Checkbox
                    checked={granted.includes(row.name)}
                    onChange={(event) => {
                      setGranted((prev) =>
                        event.target.checked
                          ? [...prev, row.name]
                          : prev.filter((c) => c !== row.name),
                      );
                    }}
                  />
                ),
              },
              {
                title: t('assistant.definition.column.tools'),
                dataIndex: 'name',
                width: 150,
              },
              {
                title: t('assistant.definition.tools.column.description'),
                dataIndex: 'description',
                ellipsis: true,
              },
              {
                title: t('assistant.definition.tools.column.risk'),
                dataIndex: 'riskLevel',
                width: 70,
                render: (v: string) => <Tag color={RISK_TAGS[v] ?? 'default'}>{v || '-'}</Tag>,
              },
              {
                title: t('assistant.definition.tools.column.permission'),
                dataIndex: 'permission',
                width: 150,
                ellipsis: true,
              },
            ]}
          />
        )}
        <div style={{ marginTop: 12, color: '#999' }}>{t('assistant.definition.tools.note')}</div>
      </Drawer>
    </>
  );
}
