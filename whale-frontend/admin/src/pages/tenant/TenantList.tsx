// 租户管理页（Wave 5.54 Phase 4）- 仅平台租户可见
// 分页查询 / 开通 / 编辑名称与配额 / 启停 / 续期；按钮按 sys:tenant:* 权限码渲染

import { App, DatePicker, Form, Input, Select, Space, Switch, Tag, type TableProps } from 'antd';
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
  changeTenantStatus,
  createTenant,
  pageTenants,
  renewTenant,
  updateTenant,
  type TenantVO,
} from '../../api/tenant';
import { useI18n } from '../../i18n';

/** DatePicker 值的结构子集（避免额外引入 dayjs 类型依赖） */
interface DateValue {
  format: (template: string) => string;
}

interface TenantCreateValues {
  tenantCode?: string;
  tenantName: string;
  adminUsername: string;
  adminPassword: string;
  adminNickname?: string;
  expireTime?: DateValue;
  quotaJson?: string;
}

interface TenantEditValues {
  tenantName?: string;
  quotaJson?: string;
}

interface TenantRenewValues {
  expireTime: DateValue;
}

interface TenantQuery {
  keyword?: string;
  status?: number;
}

const DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss';

/** DatePicker 值格式化为后端约定的 yyyy-MM-dd HH:mm:ss */
function toDateText(value?: DateValue): string | undefined {
  return value?.format(DATE_FORMAT);
}

/** 配额 JSON 摘要；非法 JSON 原样展示 */
function quotaSummary(quotaJson: string | undefined, emptyText: string): string {
  if (!quotaJson) {
    return emptyText;
  }
  try {
    const parsed = JSON.parse(quotaJson) as Record<string, unknown>;
    const entries = Object.entries(parsed);
    if (entries.length === 0) {
      return emptyText;
    }
    return entries.map(([key, value]) => `${key}: ${String(value)}`).join(' / ');
  } catch {
    return quotaJson;
  }
}

export default function TenantList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const { t } = useI18n();
  const tableRef = useRef<ProTableRef>(null);

  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [status, setStatus] = useState<number | undefined>(undefined);

  const [createOpen, setCreateOpen] = useState(false);
  const [createForm] = Form.useForm<TenantCreateValues>();

  const [editOpen, setEditOpen] = useState(false);
  const [editRecord, setEditRecord] = useState<TenantVO | null>(null);
  const [editForm] = Form.useForm<TenantEditValues>();

  const [renewOpen, setRenewOpen] = useState(false);
  const [renewRecord, setRenewRecord] = useState<TenantVO | null>(null);
  const [renewForm] = Form.useForm<TenantRenewValues>();

  const showError = (error: unknown, fallback: string) => {
    message.error(error instanceof Error ? error.message : fallback);
  };

  const handleCreate = async (values: TenantCreateValues) => {
    try {
      await createTenant({
        tenantCode: values.tenantCode ?? '',
        tenantName: values.tenantName,
        adminUsername: values.adminUsername,
        adminPassword: values.adminPassword,
        adminNickname: values.adminNickname,
        expireTime: toDateText(values.expireTime),
        quotaJson: values.quotaJson,
      });
      message.success(t('tenant.msg.created'));
      setCreateOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, t('tenant.msg.createFailed'));
      throw error;
    }
  };

  const handleEdit = async (values: TenantEditValues) => {
    if (!editRecord) {
      return;
    }
    try {
      await updateTenant(editRecord.id, {
        tenantName: values.tenantName,
        quotaJson: values.quotaJson,
      });
      message.success(t('tenant.msg.updated'));
      setEditOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, t('tenant.msg.updateFailed'));
      throw error;
    }
  };

  const handleRenew = async (values: TenantRenewValues) => {
    if (!renewRecord) {
      return;
    }
    try {
      await renewTenant(renewRecord.id, toDateText(values.expireTime) ?? '');
      message.success(t('tenant.msg.renewed'));
      setRenewOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, t('tenant.msg.renewFailed'));
      throw error;
    }
  };

  const handleToggleStatus = async (row: TenantVO, nextStatus: number) => {
    try {
      await changeTenantStatus(row.id, nextStatus);
      message.success(nextStatus === 1 ? t('tenant.msg.enabled') : t('tenant.msg.disabled'));
      tableRef.current?.reload();
    } catch (error) {
      showError(error, t('tenant.msg.statusFailed'));
    }
  };

  const statusOptions = [
    { value: 1, label: t('tenant.status.enabled') },
    { value: 0, label: t('tenant.status.disabled') },
  ];

  const columns: TableProps<TenantVO>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: t('tenant.col.tenantCode'), dataIndex: 'tenantCode', width: 160 },
    { title: t('tenant.col.tenantName'), dataIndex: 'tenantName', width: 180 },
    {
      title: t('tenant.col.tenantType'),
      dataIndex: 'tenantType',
      width: 110,
      render: (value: TenantVO['tenantType']) =>
        value === 'PLATFORM' ? (
          <Tag color="gold">{t('tenant.tag.platform')}</Tag>
        ) : (
          <Tag color="blue">{t('tenant.tag.business')}</Tag>
        ),
    },
    {
      title: t('tenant.col.status'),
      dataIndex: 'status',
      width: 100,
      render: (value: number | undefined, row) => (
        <Switch
          checked={value === 1}
          disabled={!has('sys:tenant:status') || row.tenantType === 'PLATFORM'}
          onChange={(checked) => void handleToggleStatus(row, checked ? 1 : 0)}
        />
      ),
    },
    {
      title: t('tenant.col.expireTime'),
      dataIndex: 'expireTime',
      width: 180,
      render: (value: string | undefined) => value ?? t('tenant.value.longTerm'),
    },
    {
      title: t('tenant.col.quota'),
      dataIndex: 'quotaJson',
      render: (value: string | undefined) => quotaSummary(value, t('tenant.quota.empty')),
    },
    {
      title: t('tenant.col.createTime'),
      dataIndex: 'createTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('tenant.col.action'),
      key: 'action',
      width: 180,
      render: (_, row) => (
        <Space size={4} wrap>
          <AuthButton
            perm="sys:tenant:edit"
            type="link"
            size="small"
            onClick={() => {
              setEditRecord(row);
              setEditOpen(true);
            }}
          >
            {t('tenant.action.edit')}
          </AuthButton>
          <AuthButton
            perm="sys:tenant:renew"
            type="link"
            size="small"
            onClick={() => {
              setRenewRecord(row);
              setRenewOpen(true);
            }}
          >
            {t('tenant.action.renew')}
          </AuthButton>
        </Space>
      ),
    },
  ];

  return (
    <>
      <ProTable<TenantVO, TenantQuery>
        ref={tableRef}
        rowKey="id"
        title={t('tenant.list.title')}
        columns={columns}
        request={pageTenants}
        params={{ keyword, status }}
        onError={(error) => showError(error, t('tenant.list.loadFailed'))}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: t('tenant.search.keyword'),
                element: <Input allowClear placeholder={t('tenant.list.keywordPlaceholder')} />,
              },
              {
                name: 'status',
                label: t('tenant.col.status'),
                element: (
                  <Select allowClear options={statusOptions} placeholder={t('tenant.search.all')} />
                ),
              },
            ]}
            onSearch={(values) => {
              setKeyword((values.keyword as string) || undefined);
              setStatus(typeof values.status === 'number' ? values.status : undefined);
            }}
            onReset={() => {
              setKeyword(undefined);
              setStatus(undefined);
            }}
          />
        }
        toolbar={
          <AuthButton perm="sys:tenant:add" type="primary" onClick={() => setCreateOpen(true)}>
            {t('tenant.create.title')}
          </AuthButton>
        }
      />

      <FormModal<TenantCreateValues>
        open={createOpen}
        title={t('tenant.create.title')}
        form={createForm}
        width={560}
        initialValues={{ adminUsername: 'admin' }}
        onOk={handleCreate}
        onCancel={() => setCreateOpen(false)}
      >
        <Form.Item
          name="tenantCode"
          label={t('tenant.col.tenantCode')}
          rules={[
            { required: true, message: t('tenant.create.codeRequired') },
            {
              pattern: /^[A-Za-z0-9_-]{2,64}$/,
              message: t('tenant.create.codePattern'),
            },
          ]}
        >
          <Input placeholder={t('tenant.create.codePlaceholder')} />
        </Form.Item>
        <Form.Item
          name="tenantName"
          label={t('tenant.col.tenantName')}
          rules={[{ required: true, message: t('tenant.create.nameRequired') }]}
        >
          <Input placeholder={t('tenant.create.namePlaceholder')} />
        </Form.Item>
        <Form.Item
          name="adminUsername"
          label={t('tenant.create.adminUsername')}
          rules={[{ required: true, message: t('tenant.create.adminUsernameRequired') }]}
        >
          <Input placeholder={t('tenant.create.adminUsernamePlaceholder')} />
        </Form.Item>
        <Form.Item
          name="adminPassword"
          label={t('tenant.create.adminPassword')}
          rules={[
            { required: true, message: t('tenant.create.adminPasswordRequired') },
            { min: 6, message: t('tenant.create.passwordMin') },
          ]}
        >
          <Input.Password placeholder={t('tenant.create.passwordPlaceholder')} />
        </Form.Item>
        <Form.Item name="adminNickname" label={t('tenant.create.adminNickname')}>
          <Input placeholder={t('tenant.create.optional')} />
        </Form.Item>
        <Form.Item name="expireTime" label={t('tenant.col.expireTime')}>
          <DatePicker
            showTime
            format={DATE_FORMAT}
            style={{ width: '100%' }}
            placeholder={t('tenant.create.expirePlaceholder')}
          />
        </Form.Item>
        <Form.Item name="quotaJson" label={t('tenant.field.quotaJson')}>
          <Input.TextArea rows={3} placeholder={t('tenant.create.quotaPlaceholder')} />
        </Form.Item>
      </FormModal>

      <FormModal<TenantEditValues>
        open={editOpen}
        title={t('tenant.edit.title', { name: editRecord?.tenantName ?? '' })}
        form={editForm}
        width={560}
        initialValues={editRecord ?? undefined}
        onOk={handleEdit}
        onCancel={() => setEditOpen(false)}
      >
        <Form.Item name="tenantName" label={t('tenant.col.tenantName')}>
          <Input />
        </Form.Item>
        <Form.Item name="quotaJson" label={t('tenant.field.quotaJson')}>
          <Input.TextArea rows={3} placeholder={t('tenant.create.quotaPlaceholder')} />
        </Form.Item>
      </FormModal>

      <FormModal<TenantRenewValues>
        open={renewOpen}
        title={t('tenant.renew.title', { name: renewRecord?.tenantName ?? '' })}
        form={renewForm}
        onOk={handleRenew}
        onCancel={() => setRenewOpen(false)}
      >
        <Form.Item
          name="expireTime"
          label={t('tenant.renew.expireTime')}
          rules={[{ required: true, message: t('tenant.renew.expireRequired') }]}
        >
          <DatePicker showTime format={DATE_FORMAT} style={{ width: '100%' }} />
        </Form.Item>
      </FormModal>
    </>
  );
}
