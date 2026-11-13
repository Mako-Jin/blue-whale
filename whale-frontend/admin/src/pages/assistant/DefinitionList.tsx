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

const STATUS_OPTIONS = [
  { value: 'draft', label: '草稿' },
  { value: 'published', label: '已发布' },
  { value: 'disabled', label: '已停用' },
];

const STATUS_TAGS: Record<string, { color: string; label: string }> = {
  draft: { color: 'default', label: '草稿' },
  published: { color: 'success', label: '已发布' },
  disabled: { color: 'warning', label: '已停用' },
};

const RISK_TAGS: Record<string, string> = { L0: 'green', L1: 'orange', L2: 'red' };

function renderStatus(status: string | undefined) {
  if (!status) return '-';
  const item = STATUS_TAGS[status] ?? { color: 'default', label: status };
  return <Tag color={item.color}>{item.label}</Tag>;
}

export default function DefinitionList() {
  const { message } = App.useApp();
  const tableRef = useRef<ProTableRef>(null);

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
        message.success('助手已更新');
      } else {
        await createDefinition(body);
        message.success('助手已创建（草稿态）');
      }
      setFormOpen(false);
      refreshList();
    } catch (error) {
      showError(error, '保存失败');
      throw error;
    }
  };

  const handlePublish = async (row: DefinitionVO) => {
    try {
      await publishDefinition(row.id);
      message.success('助手已发布');
      refreshList();
    } catch (error) {
      showError(error, '发布失败');
    }
  };

  const handleDisable = async (row: DefinitionVO) => {
    try {
      await disableDefinition(row.id);
      message.success('助手已停用');
      refreshList();
    } catch (error) {
      showError(error, '停用失败');
    }
  };

  const handleDelete = async (row: DefinitionVO) => {
    try {
      await deleteDefinition(row.id);
      message.success('助手已删除');
      refreshList();
    } catch (error) {
      showError(error, '删除失败');
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
      .catch((error) => showError(error, '工具清单加载失败'))
      .finally(() => setToolsLoading(false));
  };

  const handleSaveTools = async () => {
    if (!toolsRecord) {
      return;
    }
    setSavingTools(true);
    try {
      await setToolWhitelist(toolsRecord.id, granted);
      message.success('工具授权已保存');
      setToolsOpen(false);
      refreshList();
    } catch (error) {
      showError(error, '保存失败');
    } finally {
      setSavingTools(false);
    }
  };

  const columns: TableProps<DefinitionVO>['columns'] = [
    { title: '编码', dataIndex: 'code', width: 160, ellipsis: true },
    {
      title: '名称',
      dataIndex: 'name',
      width: 160,
      ellipsis: true,
      render: (v?: string) => v ?? '-',
    },
    { title: '描述', dataIndex: 'description', ellipsis: true, render: (v?: string) => v ?? '-' },
    {
      title: '工具',
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
    { title: '状态', dataIndex: 'status', width: 90, render: (v?: string) => renderStatus(v) },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 170,
      render: (v?: string) => v ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 250,
      render: (_, row) => (
        <Space size={4} wrap>
          <Button type="link" size="small" onClick={() => openTools(row)}>
            工具授权
          </Button>
          <Button type="link" size="small" onClick={() => openEdit(row)}>
            编辑
          </Button>
          {row.status === 'draft' && (
            <Popconfirm title="确认发布该助手？发布后可建会话问答">
              <Button type="link" size="small" onClick={() => void handlePublish(row)}>
                发布
              </Button>
            </Popconfirm>
          )}
          {row.status === 'published' && (
            <Popconfirm title="确认停用该助手？停用后问答入口立即拒绝">
              <Button type="link" size="small" onClick={() => void handleDisable(row)}>
                停用
              </Button>
            </Popconfirm>
          )}
          <Popconfirm title="确认删除该助手？历史会话保留但不再有入口">
            <Button type="link" size="small" danger onClick={() => void handleDelete(row)}>
              删除
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
        title="AI 助手定义"
        columns={columns}
        request={pageDefinitions}
        params={{ keyword, status }}
        onError={(error) => showError(error, '助手列表加载失败')}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: '关键字',
                element: <Input allowClear placeholder="名称 / 编码" />,
              },
              {
                name: 'status',
                label: '状态',
                element: (
                  <Select
                    allowClear
                    options={STATUS_OPTIONS}
                    placeholder="全部"
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
            新增助手
          </AuthButton>
        }
      />

      <FormModal<DefinitionFormValues>
        open={formOpen}
        title={editing ? `编辑助手：${editing.code}` : '新增助手'}
        form={form}
        initialValues={formInitial}
        onOk={handleSave}
        onCancel={() => setFormOpen(false)}
      >
        <Form.Item
          name="code"
          label="编码"
          rules={[{ required: true, message: '请输入助手编码' }]}
          extra={editing ? '编码创建后不可修改' : '租户内唯一，如 mes-helper'}
        >
          <Input placeholder="小写字母/数字/连字符" maxLength={64} disabled={Boolean(editing)} />
        </Form.Item>
        <Form.Item name="name" label="名称">
          <Input maxLength={64} placeholder="助手名称" />
        </Form.Item>
        <Form.Item name="description" label="描述">
          <Input.TextArea rows={2} maxLength={255} placeholder="助手用途说明" />
        </Form.Item>
        <Form.Item
          name="systemPrompt"
          label="系统提示词"
          extra="随请求下发 whale-ai；引用契约由平台自动追加，无需手写"
        >
          <Input.TextArea rows={4} maxLength={8000} placeholder="你是……助手" />
        </Form.Item>
        <Space size={12} wrap style={{ display: 'flex' }}>
          <Form.Item name="model" label="模型">
            <Input placeholder="留空用平台默认" style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="temperature" label="温度">
            <Input type="number" min={0} max={2} step={0.1} style={{ width: 100 }} />
          </Form.Item>
          <Form.Item name="maxTokens" label="maxTokens">
            <Input type="number" min={1} style={{ width: 120 }} />
          </Form.Item>
        </Space>
        <Form.Item name="knowledgeScope" label="知识范围" extra="逗号分隔，如 doc,code（留空不限）">
          <Input maxLength={128} placeholder="doc,code,sql" />
        </Form.Item>
      </FormModal>

      <Drawer
        title={toolsRecord ? `工具授权：${toolsRecord.code}` : '工具授权'}
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
            保存授权
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
              { title: '工具', dataIndex: 'name', width: 150 },
              { title: '说明', dataIndex: 'description', ellipsis: true },
              {
                title: '风险',
                dataIndex: 'riskLevel',
                width: 70,
                render: (v: string) => <Tag color={RISK_TAGS[v] ?? 'default'}>{v || '-'}</Tag>,
              },
              { title: '权限码', dataIndex: 'permission', width: 150, ellipsis: true },
            ]}
          />
        )}
        <div style={{ marginTop: 12, color: '#999' }}>
          白名单只能从 whale-ai 注册表选择（服务端强校验，未知工具保存会被拒绝）。
        </div>
      </Drawer>
    </>
  );
}
