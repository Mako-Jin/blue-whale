// 租户管理页（Wave 5.54 Phase 4）- 仅平台租户可见
// 分页查询 / 开通 / 编辑名称与配额 / 启停 / 续期；按钮按 sys:tenant:* 权限码渲染

import {
  App,
  DatePicker,
  Form,
  Input,
  Select,
  Space,
  Switch,
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
  changeTenantStatus,
  createTenant,
  pageTenants,
  renewTenant,
  updateTenant,
  type TenantVO,
} from '../../api/tenant';

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

const STATUS_OPTIONS = [
  { value: 1, label: '启用' },
  { value: 0, label: '停用' },
];

const DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss';

/** DatePicker 值格式化为后端约定的 yyyy-MM-dd HH:mm:ss */
function toDateText(value?: DateValue): string | undefined {
  return value?.format(DATE_FORMAT);
}

/** 配额 JSON 摘要；非法 JSON 原样展示 */
function quotaSummary(quotaJson?: string): string {
  if (!quotaJson) {
    return '未配置';
  }
  try {
    const parsed = JSON.parse(quotaJson) as Record<string, unknown>;
    const entries = Object.entries(parsed);
    if (entries.length === 0) {
      return '未配置';
    }
    return entries.map(([key, value]) => `${key}: ${String(value)}`).join(' / ');
  } catch {
    return quotaJson;
  }
}

export default function TenantList() {
  const { message } = App.useApp();
  const { has } = usePermission();
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
      message.success('租户已开通');
      setCreateOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '开通租户失败');
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
      message.success('租户已更新');
      setEditOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '更新租户失败');
      throw error;
    }
  };

  const handleRenew = async (values: TenantRenewValues) => {
    if (!renewRecord) {
      return;
    }
    try {
      await renewTenant(renewRecord.id, toDateText(values.expireTime) ?? '');
      message.success('租户已续期');
      setRenewOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '续期失败');
      throw error;
    }
  };

  const handleToggleStatus = async (row: TenantVO, nextStatus: number) => {
    try {
      await changeTenantStatus(row.id, nextStatus);
      message.success(nextStatus === 1 ? '租户已启用' : '租户已停用');
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '状态变更失败');
    }
  };

  const columns: TableProps<TenantVO>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '租户编码', dataIndex: 'tenantCode', width: 160 },
    { title: '租户名称', dataIndex: 'tenantName', width: 180 },
    {
      title: '类型',
      dataIndex: 'tenantType',
      width: 110,
      render: (value: TenantVO['tenantType']) =>
        value === 'PLATFORM' ? <Tag color="gold">平台</Tag> : <Tag color="blue">业务</Tag>,
    },
    {
      title: '状态',
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
      title: '到期时间',
      dataIndex: 'expireTime',
      width: 180,
      render: (value: string | undefined) => value ?? '长期',
    },
    {
      title: '配额',
      dataIndex: 'quotaJson',
      render: (value: string | undefined) => quotaSummary(value),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '操作',
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
            编辑
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
            续期
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
        title="租户列表"
        columns={columns}
        request={pageTenants}
        params={{ keyword, status }}
        onError={(error) => showError(error, '租户列表加载失败')}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: '关键字',
                element: <Input allowClear placeholder="租户编码 / 名称" />,
              },
              {
                name: 'status',
                label: '状态',
                element: <Select allowClear options={STATUS_OPTIONS} placeholder="全部" />,
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
            开通租户
          </AuthButton>
        }
      />

      <FormModal<TenantCreateValues>
        open={createOpen}
        title="开通租户"
        form={createForm}
        width={560}
        initialValues={{ adminUsername: 'admin' }}
        onOk={handleCreate}
        onCancel={() => setCreateOpen(false)}
      >
        <Form.Item
          name="tenantCode"
          label="租户编码"
          rules={[
            { required: true, message: '请输入租户编码' },
            {
              pattern: /^[A-Za-z0-9_-]{2,64}$/,
              message: '仅支持 2-64 位字母、数字、下划线或中划线',
            },
          ]}
        >
          <Input placeholder="登录时使用，如 acme" />
        </Form.Item>
        <Form.Item
          name="tenantName"
          label="租户名称"
          rules={[{ required: true, message: '请输入租户名称' }]}
        >
          <Input placeholder="如 示例科技有限公司" />
        </Form.Item>
        <Form.Item
          name="adminUsername"
          label="管理员用户名"
          rules={[{ required: true, message: '请输入管理员用户名' }]}
        >
          <Input placeholder="租户初始管理员账号" />
        </Form.Item>
        <Form.Item
          name="adminPassword"
          label="管理员密码"
          rules={[
            { required: true, message: '请输入管理员密码' },
            { min: 6, message: '密码至少 6 位' },
          ]}
        >
          <Input.Password placeholder="至少 6 位" />
        </Form.Item>
        <Form.Item name="adminNickname" label="管理员昵称">
          <Input placeholder="可空" />
        </Form.Item>
        <Form.Item name="expireTime" label="到期时间">
          <DatePicker showTime format={DATE_FORMAT} style={{ width: '100%' }} placeholder="留空表示长期有效" />
        </Form.Item>
        <Form.Item name="quotaJson" label="配额 JSON">
          <Input.TextArea rows={3} placeholder='如 {"maxUsers":50,"maxStorageMb":1024}' />
        </Form.Item>
      </FormModal>

      <FormModal<TenantEditValues>
        open={editOpen}
        title={`编辑租户：${editRecord?.tenantName ?? ''}`}
        form={editForm}
        width={560}
        initialValues={editRecord ?? undefined}
        onOk={handleEdit}
        onCancel={() => setEditOpen(false)}
      >
        <Form.Item name="tenantName" label="租户名称">
          <Input />
        </Form.Item>
        <Form.Item name="quotaJson" label="配额 JSON">
          <Input.TextArea rows={3} placeholder='如 {"maxUsers":50,"maxStorageMb":1024}' />
        </Form.Item>
      </FormModal>

      <FormModal<TenantRenewValues>
        open={renewOpen}
        title={`租户续期：${renewRecord?.tenantName ?? ''}`}
        form={renewForm}
        onOk={handleRenew}
        onCancel={() => setRenewOpen(false)}
      >
        <Form.Item
          name="expireTime"
          label="新到期时间"
          rules={[{ required: true, message: '请选择新的到期时间' }]}
        >
          <DatePicker showTime format={DATE_FORMAT} style={{ width: '100%' }} />
        </Form.Item>
      </FormModal>
    </>
  );
}
