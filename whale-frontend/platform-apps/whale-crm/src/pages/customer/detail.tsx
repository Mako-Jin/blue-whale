// 客户详情：360 概览 + 基本信息 + 联系人 / 地址 / 跟进记录 Tab
import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Col,
  Descriptions,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import { useNavigate, useParams } from 'react-router-dom';
import {
  createAddress,
  createContact,
  createFollowUp,
  deleteAddress,
  deleteContact,
  getCustomer360,
  getCustomerDetail,
  pageFollowUps,
  updateAddress,
  updateContact,
  type AddressSaveInput,
  type ContactSaveInput,
  type ContactView,
  type Customer360View,
  type CustomerAddressView,
  type CustomerDetailView,
  type CustomerView,
  type FollowUpView,
} from '../../api/crm';
import { hasPermission } from '../../utils/permission';
import {
  CUSTOMER_LEVEL_LABEL,
  CUSTOMER_STATUS_LABEL,
  FOLLOW_UP_CHANNEL_LABEL,
  FOLLOW_UP_CHANNEL_OPTIONS,
  POOL_STATUS_LABEL,
  formatDateTime,
} from '../../utils/dict';

/**
 * 读取错误文案。
 *
 * @param error 异常对象
 * @returns 可展示的错误信息
 */
function errorText(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请稍后重试';
}

/**
 * 将表单时间转换为后端 LocalDateTime。
 *
 * @param value 用户输入的时间
 * @returns ISO 格式时间
 */
function toIsoDateTime(value?: string): string | undefined {
  const trimmed = value?.trim();
  if (!trimmed) {
    return undefined;
  }
  return trimmed.includes('T') ? trimmed : trimmed.replace(' ', 'T');
}

/** 联系人表单值 */
interface ContactFormValues {
  name: string;
  phone?: string;
  email?: string;
  wechat?: string;
  title?: string;
  decisionRole?: string;
  decisionMaker?: boolean;
  primary?: boolean;
  status?: string;
  confirmedDuplicate?: boolean;
}

/** 地址表单值 */
interface AddressFormValues {
  contactName?: string;
  phone?: string;
  province?: string;
  city?: string;
  district?: string;
  detail: string;
  defaultShipping?: boolean;
  defaultBilling?: boolean;
}

/** 跟进表单值 */
interface FollowUpFormValues {
  channel: string;
  content: string;
  result?: string;
  nextAction?: string;
  nextActionAt?: string;
}

interface ContactModalProps {
  open: boolean;
  customerId: number;
  initial: ContactView | null;
  onCancel: () => void;
  onSaved: () => void;
}

/** 联系人新增 / 编辑弹窗 */
function ContactModal({ open, customerId, initial, onCancel, onSaved }: ContactModalProps) {
  const [form] = Form.useForm<ContactFormValues>();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    if (initial) {
      form.setFieldsValue({
        name: initial.name,
        phone: initial.phone ?? undefined,
        email: initial.email ?? undefined,
        wechat: initial.wechat ?? undefined,
        title: initial.title ?? undefined,
        decisionRole: initial.decisionRole ?? undefined,
        decisionMaker: Boolean(initial.decisionMaker),
        primary: Boolean(initial.primary),
        status: initial.status ?? 'ACTIVE',
        confirmedDuplicate: false,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({ status: 'ACTIVE', decisionMaker: false, primary: false });
    }
  }, [open, initial, form]);

  const handleOk = async () => {
    setSaving(true);
    try {
      const values = await form.validateFields();
      const payload: ContactSaveInput = {
        name: values.name,
        phone: values.phone,
        email: values.email,
        wechat: values.wechat,
        title: values.title,
        decisionRole: values.decisionRole,
        decisionMaker: Boolean(values.decisionMaker),
        primary: Boolean(values.primary),
        status: values.status,
        confirmedDuplicate: Boolean(values.confirmedDuplicate),
      };
      if (initial) {
        await updateContact(initial.id, payload);
        message.success('联系人已更新');
      } else {
        await createContact(customerId, payload);
        message.success('联系人已新增');
      }
      onSaved();
    } catch (error) {
      if (error instanceof Error) {
        message.error(errorText(error));
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      title={initial ? '编辑联系人' : '新增联系人'}
      width={640}
      okText="保存"
      cancelText="取消"
      confirmLoading={saving}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item
            name="name"
            label="姓名"
            rules={[{ required: true, message: '请输入联系人姓名' }]}
            style={{ flex: 1 }}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="title" label="职务" style={{ flex: 1 }}>
            <Input maxLength={64} />
          </Form.Item>
        </Space>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="phone" label="手机" style={{ flex: 1 }}>
            <Input maxLength={32} />
          </Form.Item>
          <Form.Item name="email" label="邮箱" style={{ flex: 1 }}>
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="wechat" label="微信" style={{ flex: 1 }}>
            <Input maxLength={64} />
          </Form.Item>
        </Space>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="decisionRole" label="决策角色" style={{ flex: 1 }}>
            <Input maxLength={64} placeholder="如 DECIDER / INFLUENCER" />
          </Form.Item>
          <Form.Item name="status" label="状态" style={{ flex: 1 }}>
            <Input maxLength={32} placeholder="ACTIVE / INACTIVE" />
          </Form.Item>
        </Space>
        <Space size={24}>
          <Form.Item name="decisionMaker" valuePropName="checked" style={{ marginBottom: 8 }}>
            <Checkbox>决策人</Checkbox>
          </Form.Item>
          <Form.Item name="primary" valuePropName="checked" style={{ marginBottom: 8 }}>
            <Checkbox>首要联系人</Checkbox>
          </Form.Item>
        </Space>
        <Form.Item name="confirmedDuplicate" valuePropName="checked" style={{ marginBottom: 0 }}>
          <Checkbox>已确认电话 / 邮箱与其他客户重复，仍然保存</Checkbox>
        </Form.Item>
      </Form>
    </Modal>
  );
}

interface AddressModalProps {
  open: boolean;
  customerId: number;
  initial: CustomerAddressView | null;
  onCancel: () => void;
  onSaved: () => void;
}

/** 地址新增 / 编辑弹窗 */
function AddressModal({ open, customerId, initial, onCancel, onSaved }: AddressModalProps) {
  const [form] = Form.useForm<AddressFormValues>();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    if (initial) {
      form.setFieldsValue({
        contactName: initial.contactName ?? undefined,
        phone: initial.phone ?? undefined,
        province: initial.province ?? undefined,
        city: initial.city ?? undefined,
        district: initial.district ?? undefined,
        detail: initial.detail ?? '',
        defaultShipping: Boolean(initial.defaultShipping),
        defaultBilling: Boolean(initial.defaultBilling),
      });
    } else {
      form.resetFields();
      form.setFieldsValue({ defaultShipping: false, defaultBilling: false });
    }
  }, [open, initial, form]);

  const handleOk = async () => {
    setSaving(true);
    try {
      const values = await form.validateFields();
      const payload: AddressSaveInput = {
        contactName: values.contactName,
        phone: values.phone,
        province: values.province,
        city: values.city,
        district: values.district,
        detail: values.detail,
        defaultShipping: Boolean(values.defaultShipping),
        defaultBilling: Boolean(values.defaultBilling),
      };
      if (initial) {
        await updateAddress(initial.id, payload);
        message.success('地址已更新');
      } else {
        await createAddress(customerId, payload);
        message.success('地址已新增');
      }
      onSaved();
    } catch (error) {
      if (error instanceof Error) {
        message.error(errorText(error));
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      title={initial ? '编辑地址' : '新增地址'}
      width={640}
      okText="保存"
      cancelText="取消"
      confirmLoading={saving}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="contactName" label="收件人" style={{ flex: 1 }}>
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="phone" label="联系电话" style={{ flex: 1 }}>
            <Input maxLength={32} />
          </Form.Item>
        </Space>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="province" label="省" style={{ flex: 1 }}>
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="city" label="市" style={{ flex: 1 }}>
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="district" label="区县" style={{ flex: 1 }}>
            <Input maxLength={64} />
          </Form.Item>
        </Space>
        <Form.Item
          name="detail"
          label="详细地址"
          rules={[{ required: true, message: '请输入详细地址' }]}
        >
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
        <Space size={24}>
          <Form.Item name="defaultShipping" valuePropName="checked" style={{ marginBottom: 0 }}>
            <Checkbox>默认收货地址</Checkbox>
          </Form.Item>
          <Form.Item name="defaultBilling" valuePropName="checked" style={{ marginBottom: 0 }}>
            <Checkbox>默认开票地址</Checkbox>
          </Form.Item>
        </Space>
      </Form>
    </Modal>
  );
}

interface FollowUpModalProps {
  open: boolean;
  customerId: number;
  onCancel: () => void;
  onSaved: () => void;
}

/** 跟进记录新增弹窗（后端 append-only） */
function FollowUpModal({ open, customerId, onCancel, onSaved }: FollowUpModalProps) {
  const [form] = Form.useForm<FollowUpFormValues>();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (open) {
      form.resetFields();
    }
  }, [open, form]);

  const handleOk = async () => {
    setSaving(true);
    try {
      const values = await form.validateFields();
      await createFollowUp({
        customerId,
        channel: values.channel,
        content: values.content,
        result: values.result,
        nextAction: values.nextAction,
        nextActionAt: toIsoDateTime(values.nextActionAt),
      });
      message.success('跟进记录已新增');
      onSaved();
    } catch (error) {
      if (error instanceof Error) {
        message.error(errorText(error));
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      title="新增跟进记录"
      width={640}
      okText="保存"
      cancelText="取消"
      confirmLoading={saving}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item
          name="channel"
          label="跟进渠道"
          rules={[{ required: true, message: '请选择跟进渠道' }]}
        >
          <Select
            placeholder="请选择跟进渠道"
            options={FOLLOW_UP_CHANNEL_OPTIONS}
            style={{ width: '100%' }}
          />
        </Form.Item>
        <Form.Item
          name="content"
          label="跟进内容"
          rules={[{ required: true, message: '请输入跟进内容' }]}
        >
          <Input.TextArea rows={3} maxLength={2000} />
        </Form.Item>
        <Form.Item name="result" label="跟进结果">
          <Input maxLength={255} />
        </Form.Item>
        <Form.Item name="nextAction" label="下一步计划">
          <Input maxLength={255} />
        </Form.Item>
        <Form.Item
          name="nextActionAt"
          label="下次跟进时间"
          extra="格式：YYYY-MM-DD HH:mm:ss（留空表示不提醒）"
        >
          <Input placeholder="2026-11-10 10:00:00" maxLength={19} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

export default function CustomerDetail() {
  const navigate = useNavigate();
  const params = useParams<{ id: string }>();
  const customerId = Number(params.id);

  const [detail, setDetail] = useState<CustomerDetailView | null>(null);
  const [overview, setOverview] = useState<Customer360View | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshKey, setRefreshKey] = useState(0);

  const [followUps, setFollowUps] = useState<FollowUpView[]>([]);
  const [followTotal, setFollowTotal] = useState(0);
  const [followLoading, setFollowLoading] = useState(false);
  const [followPage, setFollowPage] = useState({ current: 1, size: 10 });
  const [followRefresh, setFollowRefresh] = useState(0);

  const [contactModal, setContactModal] = useState(false);
  const [editingContact, setEditingContact] = useState<ContactView | null>(null);
  const [addressModal, setAddressModal] = useState(false);
  const [editingAddress, setEditingAddress] = useState<CustomerAddressView | null>(null);
  const [followModal, setFollowModal] = useState(false);

  const reload = useCallback(() => setRefreshKey((key) => key + 1), []);

  useEffect(() => {
    if (!Number.isFinite(customerId)) {
      return;
    }
    let cancelled = false;
    setLoading(true);
    getCustomerDetail(customerId)
      .then(async (result) => {
        if (cancelled) {
          return;
        }
        setDetail(result);
        const view360 = await getCustomer360(customerId).catch(() => null);
        if (!cancelled) {
          setOverview(view360);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          message.error(errorText(error));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [customerId, refreshKey]);

  useEffect(() => {
    if (!Number.isFinite(customerId) || !hasPermission('sys:crm:follow-up:list')) {
      return;
    }
    let cancelled = false;
    setFollowLoading(true);
    pageFollowUps(customerId, followPage.current, followPage.size)
      .then((result) => {
        if (!cancelled) {
          setFollowUps(result.records);
          setFollowTotal(Number(result.total));
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          message.error(errorText(error));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setFollowLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [customerId, followPage, followRefresh]);

  if (!Number.isFinite(customerId)) {
    return (
      <Card>
        <Alert type="error" message="客户 ID 不合法" showIcon />
      </Card>
    );
  }

  const customer: CustomerView | undefined = detail?.customer;

  const handleDeleteContact = async (row: ContactView) => {
    try {
      await deleteContact(row.id);
      message.success('联系人已删除');
      reload();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const handleDeleteAddress = async (row: CustomerAddressView) => {
    try {
      await deleteAddress(row.id);
      message.success('地址已删除');
      reload();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const contactColumns: TableProps<ContactView>['columns'] = [
    { title: '姓名', dataIndex: 'name', width: 120 },
    {
      title: '职务',
      dataIndex: 'title',
      width: 120,
      render: (value: string | null | undefined) => value ?? '-',
    },
    {
      title: '手机',
      dataIndex: 'phone',
      width: 140,
      render: (value: string | null | undefined) => value ?? '-',
    },
    {
      title: '邮箱',
      dataIndex: 'email',
      width: 200,
      render: (value: string | null | undefined) => value ?? '-',
    },
    {
      title: '决策角色',
      dataIndex: 'decisionRole',
      width: 120,
      render: (value: string | null | undefined) => value ?? '-',
    },
    {
      title: '决策人',
      dataIndex: 'decisionMaker',
      width: 90,
      render: (value: boolean | null | undefined) => (value ? '是' : '否'),
    },
    {
      title: '首要',
      dataIndex: 'primary',
      width: 90,
      render: (value: boolean | null | undefined) => (value ? <Tag color="blue">首要</Tag> : '-'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: string | null | undefined) =>
        value === 'ACTIVE' ? <Tag color="green">有效</Tag> : <Tag>停用</Tag>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 140,
      render: (_: unknown, row: ContactView) =>
        hasPermission('sys:crm:contact:manage') ? (
          <Space size={4}>
            <Button
              type="link"
              size="small"
              onClick={() => {
                setEditingContact(row);
                setContactModal(true);
              }}
            >
              编辑
            </Button>
            <Popconfirm
              title="删除联系人"
              description="删除后不可恢复，确认继续？"
              okText="删除"
              cancelText="取消"
              onConfirm={() => handleDeleteContact(row)}
            >
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          </Space>
        ) : null,
    },
  ];

  const addressColumns: TableProps<CustomerAddressView>['columns'] = [
    {
      title: '收件人',
      dataIndex: 'contactName',
      width: 120,
      render: (value: string | null | undefined) => value ?? '-',
    },
    {
      title: '联系电话',
      dataIndex: 'phone',
      width: 140,
      render: (value: string | null | undefined) => value ?? '-',
    },
    {
      title: '完整地址',
      dataIndex: 'fullAddress',
      render: (value: string | null | undefined) => value ?? '-',
    },
    {
      title: '默认收货',
      dataIndex: 'defaultShipping',
      width: 90,
      render: (value: boolean | null | undefined) => (value ? <Tag color="blue">是</Tag> : '-'),
    },
    {
      title: '默认开票',
      dataIndex: 'defaultBilling',
      width: 90,
      render: (value: boolean | null | undefined) => (value ? <Tag color="blue">是</Tag> : '-'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 140,
      render: (_: unknown, row: CustomerAddressView) =>
        hasPermission('sys:crm:address:manage') ? (
          <Space size={4}>
            <Button
              type="link"
              size="small"
              onClick={() => {
                setEditingAddress(row);
                setAddressModal(true);
              }}
            >
              编辑
            </Button>
            <Popconfirm
              title="删除地址"
              description="删除后不可恢复，确认继续？"
              okText="删除"
              cancelText="取消"
              onConfirm={() => handleDeleteAddress(row)}
            >
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          </Space>
        ) : null,
    },
  ];

  const followColumns: TableProps<FollowUpView>['columns'] = [
    {
      title: '跟进时间',
      dataIndex: 'createTime',
      width: 160,
      render: (value: string | null | undefined) => formatDateTime(value),
    },
    {
      title: '渠道',
      dataIndex: 'channel',
      width: 100,
      render: (value: string | null | undefined) =>
        FOLLOW_UP_CHANNEL_LABEL[value ?? ''] ?? value ?? '-',
    },
    {
      title: '跟进内容',
      dataIndex: 'content',
      render: (value: string | null | undefined) => value ?? '-',
    },
    {
      title: '结果',
      dataIndex: 'result',
      width: 160,
      render: (value: string | null | undefined) => value ?? '-',
    },
    {
      title: '下一步',
      dataIndex: 'nextAction',
      width: 160,
      render: (value: string | null | undefined) => value ?? '-',
    },
    {
      title: '下次跟进',
      dataIndex: 'nextActionAt',
      width: 160,
      render: (value: string | null | undefined) => formatDateTime(value),
    },
    {
      title: '跟进人',
      dataIndex: 'createdBy',
      width: 90,
      render: (value: number | null | undefined) => value ?? '-',
    },
  ];

  const basicItems = [
    { key: 'customerCode', label: '客户编码', children: customer?.customerCode ?? '-' },
    { key: 'name', label: '客户名称', children: customer?.name ?? '-' },
    { key: 'shortName', label: '客户简称', children: customer?.shortName ?? '-' },
    {
      key: 'level',
      label: '客户等级',
      children: CUSTOMER_LEVEL_LABEL[customer?.level ?? ''] ?? customer?.level ?? '-',
    },
    {
      key: 'status',
      label: '客户状态',
      children: CUSTOMER_STATUS_LABEL[customer?.status ?? ''] ?? customer?.status ?? '-',
    },
    {
      key: 'poolStatus',
      label: '归属状态',
      children: POOL_STATUS_LABEL[customer?.poolStatus ?? ''] ?? customer?.poolStatus ?? '-',
    },
    { key: 'ownerId', label: '负责人', children: customer?.ownerId ?? '公海' },
    { key: 'deptId', label: '归属部门', children: customer?.deptId ?? '-' },
    { key: 'source', label: '客户来源', children: customer?.source ?? '-' },
    { key: 'industry', label: '所属行业', children: customer?.industry ?? '-' },
    { key: 'scale', label: '企业规模', children: customer?.scale ?? '-' },
    { key: 'website', label: '官网', children: customer?.website ?? '-' },
    { key: 'taxNo', label: '税号', children: customer?.taxNo ?? '-' },
    { key: 'province', label: '省', children: customer?.province ?? '-' },
    { key: 'city', label: '市', children: customer?.city ?? '-' },
    { key: 'region', label: '区县', children: customer?.region ?? '-' },
    { key: 'address', label: '联系地址', children: customer?.address ?? '-' },
    { key: 'tags', label: '标签', children: customer?.tags ?? '-' },
    {
      key: 'lastFollowUpAt',
      label: '最近跟进',
      children: formatDateTime(customer?.lastFollowUpAt),
    },
    { key: 'createTime', label: '创建时间', children: formatDateTime(customer?.createTime) },
    { key: 'updateTime', label: '更新时间', children: formatDateTime(customer?.updateTime) },
  ];

  const overviewTab = (
    <Space direction="vertical" size={16} style={{ display: 'flex' }}>
      <Row gutter={16}>
        <Col span={6}>
          <Card size="small">
            <Statistic title="联系人数量" value={detail?.contacts.length ?? 0} />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic title="商机总数" value={overview?.opportunitySummary.totalCount ?? 0} />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title="商机总额"
              value={Number(overview?.opportunitySummary.totalAmount ?? 0)}
              precision={2}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title="最近跟进"
              value={formatDateTime(
                overview?.recentFollowUps[0]?.createTime ?? customer?.lastFollowUpAt,
              )}
            />
          </Card>
        </Col>
      </Row>
      {(overview?.unavailableIntegrations.length ?? 0) > 0 && (
        <Alert
          type="info"
          showIcon
          message="以下能力暂不可用"
          description={(overview?.unavailableIntegrations ?? []).join('、')}
        />
      )}
      <Card size="small" title="客户画像">
        {hasPermission('sys:crm:profile:view') ? (
          <Descriptions
            bordered
            column={2}
            size="small"
            items={[
              {
                key: 'preference',
                label: '偏好',
                children: overview?.profile.preference ?? detail?.profile.preference ?? '-',
              },
              {
                key: 'painPoints',
                label: '痛点',
                children: overview?.profile.painPoints ?? detail?.profile.painPoints ?? '-',
              },
              {
                key: 'keyPerson',
                label: '关键人',
                children: overview?.profile.keyPerson ?? detail?.profile.keyPerson ?? '-',
              },
              {
                key: 'sensitiveRemark',
                label: '敏感备注',
                children:
                  overview?.profile.sensitiveRemark ?? detail?.profile.sensitiveRemark ?? '-',
              },
              {
                key: 'decisionChain',
                label: '决策链',
                children:
                  (overview?.profile.decisionChain ?? detail?.profile.decisionChain ?? [])
                    .map(
                      (item) =>
                        `${item.role ?? '-'} / ${item.influence ?? '-'}${
                          item.remark ? `（${item.remark}）` : ''
                        }`,
                    )
                    .join('；') || '-',
              },
              {
                key: 'version',
                label: '画像版本',
                children: overview?.profile.version ?? detail?.profile.version ?? '-',
              },
            ]}
          />
        ) : (
          <Typography.Text type="secondary">无画像查看权限</Typography.Text>
        )}
      </Card>
      <Card size="small" title="最近跟进">
        {overview?.recentFollowUps.length ? (
          <Space direction="vertical" size={8} style={{ display: 'flex' }}>
            {overview.recentFollowUps.slice(0, 5).map((item) => (
              <Typography.Text key={item.id}>
                {formatDateTime(item.createTime)} ·{' '}
                {FOLLOW_UP_CHANNEL_LABEL[item.channel ?? ''] ?? item.channel ?? '-'} ·{' '}
                {item.content ?? '-'}
              </Typography.Text>
            ))}
          </Space>
        ) : (
          <Typography.Text type="secondary">暂无跟进记录</Typography.Text>
        )}
      </Card>
    </Space>
  );

  return (
    <Space direction="vertical" size={16} style={{ display: 'flex' }}>
      <Card size="small" loading={loading}>
        <Space style={{ width: '100%', justifyContent: 'space-between' }} wrap>
          <Space>
            <Button onClick={() => navigate('/customer')}>返回列表</Button>
            <Typography.Title level={5} style={{ margin: 0 }}>
              {customer?.name ?? '客户详情'}
            </Typography.Title>
            {customer && (
              <Tag color={customer.status === 'ACTIVE' ? 'green' : 'default'}>
                {CUSTOMER_STATUS_LABEL[customer.status] ?? customer.status}
              </Tag>
            )}
            {customer?.level && (
              <Tag color="blue">{CUSTOMER_LEVEL_LABEL[customer.level] ?? customer.level}</Tag>
            )}
          </Space>
          <Button onClick={reload}>刷新</Button>
        </Space>
      </Card>

      <Card size="small">
        <Tabs
          defaultActiveKey="overview"
          items={[
            { key: 'overview', label: '360 概览', children: overviewTab },
            {
              key: 'basic',
              label: '基本信息',
              children: <Descriptions bordered size="small" column={2} items={basicItems} />,
            },
            {
              key: 'contacts',
              label: `联系人（${detail?.contacts.length ?? 0}）`,
              children: (
                <Space direction="vertical" size={12} style={{ display: 'flex' }}>
                  {hasPermission('sys:crm:contact:manage') && (
                    <Button
                      type="primary"
                      onClick={() => {
                        setEditingContact(null);
                        setContactModal(true);
                      }}
                    >
                      新增联系人
                    </Button>
                  )}
                  <Table<ContactView>
                    rowKey="id"
                    size="small"
                    columns={contactColumns}
                    dataSource={detail?.contacts ?? []}
                    pagination={false}
                    scroll={{ x: 1000 }}
                  />
                </Space>
              ),
            },
            {
              key: 'addresses',
              label: `地址（${detail?.addresses.length ?? 0}）`,
              children: (
                <Space direction="vertical" size={12} style={{ display: 'flex' }}>
                  {hasPermission('sys:crm:address:manage') && (
                    <Button
                      type="primary"
                      onClick={() => {
                        setEditingAddress(null);
                        setAddressModal(true);
                      }}
                    >
                      新增地址
                    </Button>
                  )}
                  <Table<CustomerAddressView>
                    rowKey="id"
                    size="small"
                    columns={addressColumns}
                    dataSource={detail?.addresses ?? []}
                    pagination={false}
                    scroll={{ x: 800 }}
                  />
                </Space>
              ),
            },
            {
              key: 'follow-ups',
              label: '跟进记录',
              children: hasPermission('sys:crm:follow-up:list') ? (
                <Space direction="vertical" size={12} style={{ display: 'flex' }}>
                  <Space>
                    <Button onClick={() => setFollowRefresh((key) => key + 1)}>刷新</Button>
                    {hasPermission('sys:crm:follow-up:create') && (
                      <Button type="primary" onClick={() => setFollowModal(true)}>
                        新增跟进
                      </Button>
                    )}
                  </Space>
                  <Table<FollowUpView>
                    rowKey="id"
                    size="small"
                    loading={followLoading}
                    columns={followColumns}
                    dataSource={followUps}
                    scroll={{ x: 1100 }}
                    pagination={{
                      current: followPage.current,
                      pageSize: followPage.size,
                      total: followTotal,
                      showSizeChanger: true,
                      onChange: (current, size) => setFollowPage({ current, size }),
                    }}
                  />
                </Space>
              ) : (
                <Alert type="warning" showIcon message="当前账号无跟进记录查看权限" />
              ),
            },
          ]}
        />
      </Card>

      <ContactModal
        open={contactModal}
        customerId={customerId}
        initial={editingContact}
        onCancel={() => {
          setContactModal(false);
          setEditingContact(null);
        }}
        onSaved={() => {
          setContactModal(false);
          setEditingContact(null);
          reload();
        }}
      />

      <AddressModal
        open={addressModal}
        customerId={customerId}
        initial={editingAddress}
        onCancel={() => {
          setAddressModal(false);
          setEditingAddress(null);
        }}
        onSaved={() => {
          setAddressModal(false);
          setEditingAddress(null);
          reload();
        }}
      />

      <FollowUpModal
        open={followModal}
        customerId={customerId}
        onCancel={() => setFollowModal(false)}
        onSaved={() => {
          setFollowModal(false);
          setFollowRefresh((key) => key + 1);
        }}
      />
    </Space>
  );
}
