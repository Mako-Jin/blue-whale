// OAuth 客户端管理页 - 列表（注册 / 编辑 / 轮换密钥 / 停用）
// 权限码：sys:oauth:client:list(2374) / add(2375) / edit(2376) / secret(2377) / delete(2378)
// clientSecret 仅创建/轮换响应返回一次，用 Modal 强提示让管理员立即保存。

import {
  Alert,
  App,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Tag,
  Typography,
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
  createOAuthClient,
  disableOAuthClient,
  pageOAuthClients,
  rotateOAuthClientSecret,
  updateOAuthClient,
  type OAuthClientSavePayload,
  type OAuthClientSecretVO,
  type OAuthClientVO,
} from '../../../api/oauthClient';

type ClientQuery = { keyword?: string; status?: number };

const GRANT_TYPES = [
  { value: 'authorization_code', label: 'authorization_code' },
  { value: 'refresh_token', label: 'refresh_token' },
  { value: 'client_credentials', label: 'client_credentials' },
];

const SCOPE_OPTIONS = ['openid', 'profile', 'email', 'offline_access'].map((scope) => ({
  value: scope,
  label: scope,
}));

const STATUS_OPTIONS = [
  { value: 1, label: '启用' },
  { value: 0, label: '停用' },
];

const { Paragraph } = Typography;

interface ClientFormValues {
  clientId: string;
  clientName: string;
  redirectUris?: string[];
  grantTypes?: string[];
  scopes?: string[];
  accessTokenTtl?: number;
  refreshTokenTtl?: number;
  autoApprove?: boolean;
  status?: number;
}

/** 秘密展示弹窗：clientSecret 仅此一次返回 */
function showSecretModal(modal: ReturnType<typeof Modal.useModal>[0], result: OAuthClientSecretVO) {
  modal.warning({
    title: '客户端密钥（仅此一次展示，请立即保存）',
    width: 560,
    content: (
      <>
        <Paragraph copyable style={{ marginBottom: 8 }}>
          client_id: {result.clientId}
        </Paragraph>
        <Paragraph copyable>
          client_secret: {result.clientSecret}
        </Paragraph>
        <Alert
          style={{ marginTop: 12 }}
          type="warning"
          showIcon
          message="关闭本弹窗后密钥不可再次查看，只能重新轮换。"
        />
      </>
    ),
    okText: '我已保存',
  });
}

export default function OAuthClientList() {
  const { message } = App.useApp();
  const { modal } = App.useApp();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);

  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [status, setStatus] = useState<number | undefined>(undefined);

  const [editing, setEditing] = useState<OAuthClientVO | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [formInitial, setFormInitial] = useState<Partial<ClientFormValues> | undefined>(undefined);
  const [form] = Form.useForm<ClientFormValues>();
  const [rotating, setRotating] = useState<string | null>(null);

  const showError = (error: unknown, fallback: string) => {
    message.error(error instanceof Error ? error.message : fallback);
  };

  const openCreate = () => {
    setEditing(null);
    setFormInitial({
      grantTypes: ['authorization_code', 'refresh_token'],
      scopes: ['openid', 'profile'],
      accessTokenTtl: 900,
      refreshTokenTtl: 604800,
      autoApprove: false,
      status: 1,
    });
    setFormOpen(true);
  };

  const openEdit = (record: OAuthClientVO) => {
    setEditing(record);
    setFormInitial({
      clientId: record.clientId,
      clientName: record.clientName,
      redirectUris: record.redirectUris,
      grantTypes: record.grantTypes,
      scopes: record.scopes,
      accessTokenTtl: record.accessTokenTtl,
      refreshTokenTtl: record.refreshTokenTtl,
      autoApprove: record.autoApprove,
      status: record.status ?? 1,
    });
    setFormOpen(true);
  };

  const handleSave = async (values: ClientFormValues) => {
    const payload: OAuthClientSavePayload = { ...values };
    try {
      if (editing) {
        const updated = await updateOAuthClient(editing.id, payload);
        message.success(`客户端 ${updated.clientId} 已更新`);
      } else {
        const secret = await createOAuthClient(payload);
        setFormOpen(false);
        tableRef.current?.reload();
        showSecretModal(modal, secret);
        return;
      }
      setFormOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '保存失败');
      throw error;
    }
  };

  const handleRotate = async (row: OAuthClientVO) => {
    setRotating(row.id);
    try {
      const secret = await rotateOAuthClientSecret(row.id);
      showSecretModal(modal, secret);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '轮换密钥失败');
    } finally {
      setRotating(null);
    }
  };

  const handleDisable = async (row: OAuthClientVO) => {
    try {
      await disableOAuthClient(row.id);
      message.success(`客户端 ${row.clientId} 已停用`);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '停用失败');
    }
  };

  const columns: TableProps<OAuthClientVO>['columns'] = [
    { title: 'client_id', dataIndex: 'clientId', width: 180, ellipsis: true },
    { title: '名称', dataIndex: 'clientName', width: 160, ellipsis: true },
    {
      title: '回调地址',
      dataIndex: 'redirectUris',
      ellipsis: true,
      render: (value: string[] | undefined) =>
        value?.length ? (
          <Space size={4} wrap>
            {value.slice(0, 2).map((uri) => (
              <Tag key={uri} style={{ maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {uri}
              </Tag>
            ))}
            {value.length > 2 ? <span>+{value.length - 2}</span> : null}
          </Space>
        ) : (
          '-'
        ),
    },
    {
      title: '授权类型',
      dataIndex: 'grantTypes',
      width: 200,
      render: (value: string[] | undefined) =>
        value?.length ? <Tag>{value.join(', ')}</Tag> : '-',
    },
    {
      title: 'Access TTL',
      dataIndex: 'accessTokenTtl',
      width: 110,
      align: 'center',
      render: (value: number | undefined) => (value ? `${value}s` : '-'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (value: number | undefined) =>
        value === 0 ? <Tag color="default">停用</Tag> : <Tag color="success">启用</Tag>,
    },
    {
      title: '操作',
      key: 'action',
      width: 210,
      render: (_, row) => (
        <Space size={4}>
          {has('sys:oauth:client:secret') && row.status !== 0 && (
            <Popconfirm
              title="确认轮换密钥？"
              description="旧密钥立即失效，新密钥仅返回一次"
              onConfirm={() => void handleRotate(row)}
            >
              <Button type="link" size="small" loading={rotating === row.id}>
                轮换密钥
              </Button>
            </Popconfirm>
          )}
          {has('sys:oauth:client:edit') && row.status !== 0 && (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              编辑
            </Button>
          )}
          {has('sys:oauth:client:delete') && row.status !== 0 && (
            <Popconfirm
              title={`确认停用 ${row.clientId}？`}
              description="停用后该客户端无法再走 OAuth 授权"
              onConfirm={() => void handleDisable(row)}
            >
              <Button type="link" size="small" danger>
                停用
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <ProTable<OAuthClientVO, ClientQuery>
        ref={tableRef}
        rowKey="id"
        title="OAuth 客户端"
        columns={columns}
        request={pageOAuthClients}
        params={{ keyword, status }}
        onError={(error) => showError(error, 'OAuth 客户端加载失败')}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: '关键字',
                element: <Input allowClear placeholder="client_id / 名称" />,
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
              setStatus((values.status as number | undefined) ?? undefined);
            }}
            onReset={() => {
              setKeyword(undefined);
              setStatus(undefined);
            }}
          />
        }
        toolbar={
          <AuthButton perm="sys:oauth:client:add" type="primary" onClick={openCreate}>
            注册客户端
          </AuthButton>
        }
      />

      <FormModal<ClientFormValues>
        open={formOpen}
        title={editing ? `编辑客户端：${editing.clientName}` : '注册客户端'}
        form={form}
        initialValues={formInitial}
        width={680}
        onOk={handleSave}
        onCancel={() => setFormOpen(false)}
      >
        <Space style={{ display: 'flex' }}>
          <Form.Item
            name="clientId"
            label="client_id"
            rules={[{ required: true, message: '请输入 client_id' }]}
          >
            <Input maxLength={64} disabled={Boolean(editing)} style={{ width: 200 }} />
          </Form.Item>
          <Form.Item
            name="clientName"
            label="名称"
            rules={[{ required: true, message: '请输入名称' }]}
          >
            <Input maxLength={64} style={{ width: 200 }} />
          </Form.Item>
        </Space>
        <Form.Item
          name="redirectUris"
          label="回调地址"
          extra="逐条输入后回车；精确匹配，支持 https 或自定义 scheme"
          rules={[{ required: true, message: '至少填写一个回调地址' }]}
        >
          <Select mode="tags" allowClear open={false} placeholder="https://app.example.com/callback" />
        </Form.Item>
        <Form.Item
          name="grantTypes"
          label="授权类型"
          rules={[{ required: true, message: '至少选择一种授权类型' }]}
        >
          <Select mode="multiple" options={GRANT_TYPES} />
        </Form.Item>
        <Form.Item name="scopes" label="授权范围">
          <Select mode="multiple" allowClear options={SCOPE_OPTIONS} placeholder="openid / profile ..." />
        </Form.Item>
        <Space style={{ display: 'flex' }}>
          <Form.Item name="accessTokenTtl" label="Access TTL" extra="秒">
            <InputNumber min={60} style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="refreshTokenTtl" label="Refresh TTL" extra="秒">
            <InputNumber min={60} style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="autoApprove" label="自动授权">
            <Select
              options={[
                { value: true, label: '是（跳过确认页）' },
                { value: false, label: '否' },
              ]}
              style={{ width: 160 }}
            />
          </Form.Item>
          <Form.Item name="status" label="状态" initialValue={1}>
            <Select options={STATUS_OPTIONS} style={{ width: 120 }} />
          </Form.Item>
        </Space>
      </FormModal>
    </>
  );
}
