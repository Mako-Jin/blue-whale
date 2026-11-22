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
import { useI18n } from '../../../i18n';

type ClientQuery = { keyword?: string; status?: number };

type Translate = ReturnType<typeof useI18n>['t'];

const GRANT_TYPES = [
  { value: 'authorization_code', label: 'authorization_code' },
  { value: 'refresh_token', label: 'refresh_token' },
  { value: 'client_credentials', label: 'client_credentials' },
];

const SCOPE_OPTIONS = ['openid', 'profile', 'email', 'offline_access'].map((scope) => ({
  value: scope,
  label: scope,
}));

const buildStatusOptions = (t: Translate) => [
  { value: 1, label: t('systemAuth.oauth.status.enabled') },
  { value: 0, label: t('systemAuth.oauth.status.disabled') },
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
function showSecretModal(
  modal: ReturnType<typeof Modal.useModal>[0],
  result: OAuthClientSecretVO,
  t: Translate,
) {
  modal.warning({
    title: t('systemAuth.oauth.secretTitle'),
    width: 560,
    content: (
      <>
        <Paragraph copyable style={{ marginBottom: 8 }}>
          client_id: {result.clientId}
        </Paragraph>
        <Paragraph copyable>client_secret: {result.clientSecret}</Paragraph>
        <Alert
          style={{ marginTop: 12 }}
          type="warning"
          showIcon
          message={t('systemAuth.oauth.secretWarning')}
        />
      </>
    ),
    okText: t('systemAuth.oauth.secretSaved'),
  });
}

export default function OAuthClientList() {
  const { message } = App.useApp();
  const { modal } = App.useApp();
  const { t } = useI18n();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);

  const statusOptions = buildStatusOptions(t);

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
        message.success(t('systemAuth.oauth.updated', { clientId: updated.clientId }));
      } else {
        const secret = await createOAuthClient(payload);
        setFormOpen(false);
        tableRef.current?.reload();
        showSecretModal(modal, secret, t);
        return;
      }
      setFormOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, t('systemAuth.oauth.saveFailed'));
      throw error;
    }
  };

  const handleRotate = async (row: OAuthClientVO) => {
    setRotating(row.id);
    try {
      const secret = await rotateOAuthClientSecret(row.id);
      showSecretModal(modal, secret, t);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, t('systemAuth.oauth.rotateFailed'));
    } finally {
      setRotating(null);
    }
  };

  const handleDisable = async (row: OAuthClientVO) => {
    try {
      await disableOAuthClient(row.id);
      message.success(t('systemAuth.oauth.disabled', { clientId: row.clientId }));
      tableRef.current?.reload();
    } catch (error) {
      showError(error, t('systemAuth.oauth.disableFailed'));
    }
  };

  const columns: TableProps<OAuthClientVO>['columns'] = [
    { title: 'client_id', dataIndex: 'clientId', width: 180, ellipsis: true },
    {
      title: t('systemAuth.oauth.col.name'),
      dataIndex: 'clientName',
      width: 160,
      ellipsis: true,
    },
    {
      title: t('systemAuth.oauth.col.redirectUris'),
      dataIndex: 'redirectUris',
      ellipsis: true,
      render: (value: string[] | undefined) =>
        value?.length ? (
          <Space size={4} wrap>
            {value.slice(0, 2).map((uri) => (
              <Tag
                key={uri}
                style={{ maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis' }}
              >
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
      title: t('systemAuth.oauth.col.grantTypes'),
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
      title: t('systemAuth.oauth.col.status'),
      dataIndex: 'status',
      width: 80,
      render: (value: number | undefined) =>
        value === 0 ? (
          <Tag color="default">{t('systemAuth.oauth.status.disabled')}</Tag>
        ) : (
          <Tag color="success">{t('systemAuth.oauth.status.enabled')}</Tag>
        ),
    },
    {
      title: t('systemAuth.oauth.col.action'),
      key: 'action',
      width: 210,
      render: (_, row) => (
        <Space size={4}>
          {has('sys:oauth:client:secret') && row.status !== 0 && (
            <Popconfirm
              title={t('systemAuth.oauth.confirmRotate')}
              description={t('systemAuth.oauth.rotateDescription')}
              onConfirm={() => void handleRotate(row)}
            >
              <Button type="link" size="small" loading={rotating === row.id}>
                {t('systemAuth.oauth.rotate')}
              </Button>
            </Popconfirm>
          )}
          {has('sys:oauth:client:edit') && row.status !== 0 && (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              {t('systemAuth.oauth.edit')}
            </Button>
          )}
          {has('sys:oauth:client:delete') && row.status !== 0 && (
            <Popconfirm
              title={t('systemAuth.oauth.confirmDisable', { clientId: row.clientId })}
              description={t('systemAuth.oauth.disableDescription')}
              onConfirm={() => void handleDisable(row)}
            >
              <Button type="link" size="small" danger>
                {t('systemAuth.oauth.disable')}
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
        title={t('systemAuth.oauth.title')}
        columns={columns}
        request={pageOAuthClients}
        params={{ keyword, status }}
        onError={(error) => showError(error, t('systemAuth.oauth.loadFailed'))}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: t('systemAuth.oauth.search.keyword'),
                element: (
                  <Input allowClear placeholder={t('systemAuth.oauth.search.keywordPlaceholder')} />
                ),
              },
              {
                name: 'status',
                label: t('systemAuth.oauth.field.status'),
                element: (
                  <Select
                    allowClear
                    options={statusOptions}
                    placeholder={t('systemAuth.oauth.search.all')}
                    style={{ width: 120 }}
                  />
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
            {t('systemAuth.oauth.register')}
          </AuthButton>
        }
      />

      <FormModal<ClientFormValues>
        open={formOpen}
        title={
          editing
            ? t('systemAuth.oauth.editTitle', { name: editing.clientName })
            : t('systemAuth.oauth.register')
        }
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
            rules={[{ required: true, message: t('systemAuth.oauth.field.clientIdRequired') }]}
          >
            <Input maxLength={64} disabled={Boolean(editing)} style={{ width: 200 }} />
          </Form.Item>
          <Form.Item
            name="clientName"
            label={t('systemAuth.oauth.field.name')}
            rules={[{ required: true, message: t('systemAuth.oauth.field.nameRequired') }]}
          >
            <Input maxLength={64} style={{ width: 200 }} />
          </Form.Item>
        </Space>
        <Form.Item
          name="redirectUris"
          label={t('systemAuth.oauth.field.redirectUris')}
          extra={t('systemAuth.oauth.field.redirectUrisExtra')}
          rules={[{ required: true, message: t('systemAuth.oauth.field.redirectUrisRequired') }]}
        >
          <Select
            mode="tags"
            allowClear
            open={false}
            placeholder="https://app.example.com/callback"
          />
        </Form.Item>
        <Form.Item
          name="grantTypes"
          label={t('systemAuth.oauth.field.grantTypes')}
          rules={[{ required: true, message: t('systemAuth.oauth.field.grantTypesRequired') }]}
        >
          <Select mode="multiple" options={GRANT_TYPES} />
        </Form.Item>
        <Form.Item name="scopes" label={t('systemAuth.oauth.field.scopes')}>
          <Select
            mode="multiple"
            allowClear
            options={SCOPE_OPTIONS}
            placeholder="openid / profile ..."
          />
        </Form.Item>
        <Space style={{ display: 'flex' }}>
          <Form.Item
            name="accessTokenTtl"
            label="Access TTL"
            extra={t('systemAuth.oauth.field.seconds')}
          >
            <InputNumber min={60} style={{ width: 140 }} />
          </Form.Item>
          <Form.Item
            name="refreshTokenTtl"
            label="Refresh TTL"
            extra={t('systemAuth.oauth.field.seconds')}
          >
            <InputNumber min={60} style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="autoApprove" label={t('systemAuth.oauth.field.autoApprove')}>
            <Select
              options={[
                { value: true, label: t('systemAuth.oauth.field.autoApproveYes') },
                { value: false, label: t('systemAuth.oauth.field.autoApproveNo') },
              ]}
              style={{ width: 160 }}
            />
          </Form.Item>
          <Form.Item name="status" label={t('systemAuth.oauth.field.status')} initialValue={1}>
            <Select options={statusOptions} style={{ width: 120 }} />
          </Form.Item>
        </Space>
      </FormModal>
    </>
  );
}
