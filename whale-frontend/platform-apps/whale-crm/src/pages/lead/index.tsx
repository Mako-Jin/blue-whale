// 线索管理：筛选分页、新建编辑、状态推进、分配、无效、转化与线索动态
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Checkbox,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import {
  addLeadActivity,
  advanceLeadStatus,
  assignLead,
  convertLead,
  createLead,
  invalidateLead,
  pageLeadActivities,
  pageLeads,
  updateLead,
  type LeadActivityView,
  type LeadPageQuery,
  type LeadSaveInput,
  type LeadView,
} from '../../api/crm';
import { hasPermission } from '../../utils/permission';
import {
  FOLLOW_UP_CHANNEL_LABEL,
  FOLLOW_UP_CHANNEL_OPTIONS,
  LEAD_STATUS_COLOR,
  LEAD_STATUS_LABEL,
  LEAD_STATUS_OPTIONS,
  formatDateTime,
} from '../../utils/dict';

/** 筛选条件 */
interface LeadFilterValues {
  keyword?: string;
  status?: string;
  source?: string;
  ownerId?: number | null;
}

/** 新建 / 编辑表单值 */
interface LeadFormValues {
  name: string;
  source?: string;
  industry?: string;
  region?: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  ownerId?: number | null;
  deptId?: number | null;
  remark?: string;
  confirmedDuplicate?: boolean;
}

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
 * 线索是否已结束（转化 / 无效）。
 *
 * @param status 线索状态
 * @returns 是否不可再操作
 */
function isLeadClosed(status?: string | null): boolean {
  return status === 'CONVERTED' || status === 'INVALID';
}

/**
 * 计算可推进的下一状态。
 *
 * @param status 当前状态
 * @returns 允许推进的状态列表
 */
function nextLeadStatuses(status?: string | null): string[] {
  if (status === 'NEW') {
    return ['CONTACTED'];
  }
  if (status === 'CONTACTED') {
    return ['QUALIFIED'];
  }
  return [];
}

interface LeadFormModalProps {
  open: boolean;
  initial: LeadView | null;
  onCancel: () => void;
  onSaved: () => void;
}

/** 线索新建 / 编辑弹窗 */
function LeadFormModal({ open, initial, onCancel, onSaved }: LeadFormModalProps) {
  const [form] = Form.useForm<LeadFormValues>();
  const [saving, setSaving] = useState(false);
  const canEdit = Boolean(initial) && hasPermission('sys:crm:lead:update');
  const canCreate = !initial && hasPermission('sys:crm:lead:create');
  const sensitiveVisible = hasPermission('sys:crm:sensitive:view');
  const maskedPhone = Boolean(initial?.contactPhone?.includes('****'));
  const maskedEmail = Boolean(initial?.contactEmail?.includes('****'));

  useEffect(() => {
    if (!open) {
      return;
    }
    if (initial) {
      form.setFieldsValue({
        name: initial.name,
        source: initial.source ?? undefined,
        industry: initial.industry ?? undefined,
        region: initial.region ?? undefined,
        contactName: initial.contactName ?? undefined,
        // 无敏感明文权限时不回填脱敏值，留空由后端保持原值
        contactPhone: sensitiveVisible ? (initial.contactPhone ?? undefined) : undefined,
        contactEmail: sensitiveVisible ? (initial.contactEmail ?? undefined) : undefined,
        ownerId: initial.ownerId ?? null,
        deptId: initial.deptId ?? null,
        remark: initial.remark ?? undefined,
        confirmedDuplicate: false,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({ confirmedDuplicate: false });
    }
  }, [open, initial, form, sensitiveVisible]);

  const handleOk = async () => {
    setSaving(true);
    try {
      const values = await form.validateFields();
      const payload: LeadSaveInput = {
        name: values.name,
        source: values.source,
        industry: values.industry,
        region: values.region,
        contactName: values.contactName,
        contactPhone: values.contactPhone,
        contactEmail: values.contactEmail,
        ownerId: values.ownerId ?? null,
        deptId: values.deptId ?? null,
        remark: values.remark,
        confirmedDuplicate: Boolean(values.confirmedDuplicate),
      };
      if (initial) {
        await updateLead(initial.id, payload);
        message.success('线索已更新');
      } else {
        await createLead(payload);
        message.success('线索已创建');
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
      title={initial ? `编辑线索：${initial.name}` : '新建线索'}
      width={720}
      confirmLoading={saving}
      okText="保存"
      cancelText="取消"
      okButtonProps={{ disabled: initial ? !canEdit : !canCreate }}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item
            name="name"
            label="线索名称"
            rules={[{ required: true, message: '请输入线索名称' }]}
            style={{ flex: 1 }}
          >
            <Input placeholder="如：某集团采购意向" maxLength={100} />
          </Form.Item>
          <Form.Item name="source" label="来源" style={{ flex: 1 }}>
            <Input placeholder="如：展会 / 官网 / 转介绍" maxLength={50} />
          </Form.Item>
        </Space>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="industry" label="行业" style={{ flex: 1 }}>
            <Input maxLength={50} />
          </Form.Item>
          <Form.Item name="region" label="区域" style={{ flex: 1 }}>
            <Input maxLength={50} />
          </Form.Item>
        </Space>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="contactName" label="联系人" style={{ flex: 1 }}>
            <Input maxLength={50} />
          </Form.Item>
          <Form.Item
            name="contactPhone"
            label="联系电话"
            style={{ flex: 1 }}
            extra={
              maskedPhone
                ? `当前：${initial?.contactPhone ?? '-'}（无明文权限，留空不修改）`
                : undefined
            }
          >
            <Input maxLength={30} />
          </Form.Item>
          <Form.Item
            name="contactEmail"
            label="联系邮箱"
            style={{ flex: 1 }}
            extra={
              maskedEmail
                ? `当前：${initial?.contactEmail ?? '-'}（无明文权限，留空不修改）`
                : undefined
            }
          >
            <Input maxLength={100} />
          </Form.Item>
        </Space>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="ownerId" label="负责人 ID" style={{ flex: 1 }}>
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="deptId" label="部门 ID" style={{ flex: 1 }}>
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
        </Space>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={3} maxLength={500} showCount />
        </Form.Item>
        <Form.Item name="confirmedDuplicate" valuePropName="checked">
          <Checkbox>已确认与现有线索重复，仍然保存</Checkbox>
        </Form.Item>
      </Form>
    </Modal>
  );
}

interface LeadStatusModalProps {
  lead: LeadView | null;
  onCancel: () => void;
  onSaved: () => void;
}

/** 线索状态推进弹窗 */
function LeadStatusModal({ lead, onCancel, onSaved }: LeadStatusModalProps) {
  const [form] = Form.useForm<{ status: string }>();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (lead) {
      const options = nextLeadStatuses(lead.status);
      form.setFieldsValue({ status: options[0] });
    }
  }, [lead, form]);

  const handleOk = async () => {
    if (!lead) {
      return;
    }
    setSaving(true);
    try {
      const values = await form.validateFields();
      await advanceLeadStatus(lead.id, values.status);
      message.success('线索状态已更新');
      onSaved();
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={Boolean(lead)}
      title={lead ? `推进线索状态：${lead.name}` : '推进线索状态'}
      confirmLoading={saving}
      okText="确认推进"
      cancelText="取消"
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item
          name="status"
          label="目标状态"
          rules={[{ required: true, message: '请选择目标状态' }]}
        >
          <Select
            options={nextLeadStatuses(lead?.status).map((value) => ({
              value,
              label: LEAD_STATUS_LABEL[value] ?? value,
            }))}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}

interface LeadAssignModalProps {
  lead: LeadView | null;
  onCancel: () => void;
  onSaved: () => void;
}

/** 线索分配弹窗 */
function LeadAssignModal({ lead, onCancel, onSaved }: LeadAssignModalProps) {
  const [form] = Form.useForm<{ ownerId: number; deptId?: number | null }>();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (lead) {
      form.resetFields();
    }
  }, [lead, form]);

  const handleOk = async () => {
    if (!lead) {
      return;
    }
    setSaving(true);
    try {
      const values = await form.validateFields();
      await assignLead(lead.id, {
        ownerId: values.ownerId,
        deptId: values.deptId ?? null,
      });
      message.success('线索已分配');
      onSaved();
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={Boolean(lead)}
      title={lead ? `分配线索：${lead.name}` : '分配线索'}
      confirmLoading={saving}
      okText="确认分配"
      cancelText="取消"
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item
          name="ownerId"
          label="负责人 ID"
          rules={[{ required: true, message: '请输入负责人 ID' }]}
        >
          <InputNumber min={1} precision={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="deptId" label="部门 ID">
          <InputNumber min={1} precision={0} style={{ width: '100%' }} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

interface LeadInvalidModalProps {
  lead: LeadView | null;
  onCancel: () => void;
  onSaved: () => void;
}

/** 线索标记无效弹窗 */
function LeadInvalidModal({ lead, onCancel, onSaved }: LeadInvalidModalProps) {
  const [form] = Form.useForm<{ reason: string }>();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (lead) {
      form.resetFields();
    }
  }, [lead, form]);

  const handleOk = async () => {
    if (!lead) {
      return;
    }
    setSaving(true);
    try {
      const values = await form.validateFields();
      await invalidateLead(lead.id, values.reason);
      message.success('线索已标记无效');
      onSaved();
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={Boolean(lead)}
      title={lead ? `标记无效：${lead.name}` : '标记无效'}
      confirmLoading={saving}
      okText="确认无效"
      cancelText="取消"
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item
          name="reason"
          label="无效原因"
          rules={[{ required: true, message: '请输入无效原因' }]}
        >
          <Input.TextArea rows={3} maxLength={200} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** 线索转化表单值 */
interface LeadConvertFormValues {
  customerId?: number | null;
  opportunityName?: string;
  amount?: number | null;
  expectedCloseDate?: string;
}

interface LeadConvertModalProps {
  lead: LeadView | null;
  onCancel: () => void;
  onSaved: () => void;
}

/** 线索转化弹窗 */
function LeadConvertModal({ lead, onCancel, onSaved }: LeadConvertModalProps) {
  const [form] = Form.useForm<LeadConvertFormValues>();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (lead) {
      form.resetFields();
      form.setFieldsValue({ opportunityName: lead.name });
    }
  }, [lead, form]);

  const handleOk = async () => {
    if (!lead) {
      return;
    }
    setSaving(true);
    try {
      const values = await form.validateFields();
      const result = await convertLead(lead.id, {
        customerId: values.customerId ?? null,
        opportunityName: values.opportunityName,
        amount: values.amount ?? null,
        expectedCloseDate: values.expectedCloseDate || undefined,
      });
      if (result.alreadyConverted) {
        message.info('该线索已转化，返回既有转化结果');
      } else {
        message.success(
          `线索已转化，客户 ID：${result.customerId ?? '-'}，商机 ID：${result.opportunityId ?? '-'}`,
        );
      }
      onSaved();
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={Boolean(lead)}
      title={lead ? `线索转化：${lead.name}` : '线索转化'}
      width={640}
      confirmLoading={saving}
      okText="确认转化"
      cancelText="取消"
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item name="customerId" label="既有客户 ID" extra="留空则按线索联系信息新建客户">
          <InputNumber min={1} precision={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="opportunityName" label="商机名称">
          <Input maxLength={100} placeholder="默认使用线索名称" />
        </Form.Item>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="amount" label="商机金额" style={{ flex: 1 }}>
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="expectedCloseDate" label="预计关闭日期" style={{ flex: 1 }}>
            <Input placeholder="yyyy-MM-dd" />
          </Form.Item>
        </Space>
      </Form>
    </Modal>
  );
}

/** 线索活动表单值 */
interface LeadActivityFormValues {
  channel: string;
  content: string;
  result?: string;
  nextActionAt?: string;
}

interface LeadActivityDrawerProps {
  lead: LeadView | null;
  onClose: () => void;
  onChanged: () => void;
}

/** 线索动态抽屉（活动列表 + 追加活动） */
function LeadActivityDrawer({ lead, onClose, onChanged }: LeadActivityDrawerProps) {
  const [form] = Form.useForm<LeadActivityFormValues>();
  const [rows, setRows] = useState<LeadActivityView[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [adding, setAdding] = useState(false);
  const [addOpen, setAddOpen] = useState(false);
  const canUpdate = hasPermission('sys:crm:lead:update');

  const loadActivities = useCallback(async () => {
    if (!lead) {
      return;
    }
    setLoading(true);
    try {
      const result = await pageLeadActivities(lead.id, current, size);
      setRows(result.records);
      setTotal(Number(result.total));
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [lead, current, size]);

  useEffect(() => {
    void loadActivities();
  }, [loadActivities]);

  const handleAdd = async () => {
    if (!lead) {
      return;
    }
    setAdding(true);
    try {
      const values = await form.validateFields();
      await addLeadActivity(lead.id, {
        channel: values.channel,
        content: values.content,
        result: values.result,
        nextActionAt: values.nextActionAt || undefined,
      });
      message.success('动态已记录');
      setAddOpen(false);
      form.resetFields();
      await loadActivities();
      onChanged();
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setAdding(false);
    }
  };

  const columns: TableProps<LeadActivityView>['columns'] = [
    {
      title: '渠道',
      dataIndex: 'channel',
      width: 100,
      render: (value?: string | null) => FOLLOW_UP_CHANNEL_LABEL[value ?? ''] ?? value ?? '-',
    },
    { title: '内容', dataIndex: 'content', ellipsis: true },
    {
      title: '结果',
      dataIndex: 'result',
      width: 160,
      ellipsis: true,
      render: (value?: string | null) => value || '-',
    },
    {
      title: '下次行动',
      dataIndex: 'nextActionAt',
      width: 170,
      render: (value?: string | null) => formatDateTime(value),
    },
    {
      title: '操作人',
      dataIndex: 'createdBy',
      width: 110,
      render: (value?: number | null) => value ?? '-',
    },
    {
      title: '记录时间',
      dataIndex: 'createTime',
      width: 170,
      render: (value?: string | null) => formatDateTime(value),
    },
  ];

  return (
    <Drawer
      open={Boolean(lead)}
      title={lead ? `线索动态：${lead.name}` : '线索动态'}
      width={900}
      onClose={onClose}
      extra={
        canUpdate ? (
          <Button type="primary" onClick={() => setAddOpen(true)}>
            记录动态
          </Button>
        ) : null
      }
    >
      <Table<LeadActivityView>
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{
          current,
          pageSize: size,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (nextCurrent, nextSize) => {
            setCurrent(nextCurrent);
            setSize(nextSize);
          },
        }}
      />
      <Modal
        open={addOpen}
        title="记录线索动态"
        confirmLoading={adding}
        okText="保存"
        cancelText="取消"
        onOk={handleAdd}
        onCancel={() => {
          setAddOpen(false);
          form.resetFields();
        }}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="channel"
            label="跟进渠道"
            rules={[{ required: true, message: '请选择跟进渠道' }]}
          >
            <Select options={FOLLOW_UP_CHANNEL_OPTIONS} placeholder="请选择" />
          </Form.Item>
          <Form.Item
            name="content"
            label="跟进内容"
            rules={[{ required: true, message: '请输入跟进内容' }]}
          >
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
          <Form.Item name="result" label="跟进结果">
            <Input maxLength={200} />
          </Form.Item>
          <Form.Item name="nextActionAt" label="下次行动时间">
            <Input placeholder="yyyy-MM-dd HH:mm:ss" />
          </Form.Item>
        </Form>
      </Modal>
    </Drawer>
  );
}

/** 线索管理页 */
export default function LeadList() {
  const [form] = Form.useForm<LeadFilterValues>();
  const [rows, setRows] = useState<LeadView[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState<LeadPageQuery>({});
  const [page, setPage] = useState({ current: 1, size: 10 });
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<LeadView | null>(null);
  const [statusLead, setStatusLead] = useState<LeadView | null>(null);
  const [assignTarget, setAssignTarget] = useState<LeadView | null>(null);
  const [invalidTarget, setInvalidTarget] = useState<LeadView | null>(null);
  const [convertTarget, setConvertTarget] = useState<LeadView | null>(null);
  const [activityLead, setActivityLead] = useState<LeadView | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageLeads({ ...filters, current: page.current, size: page.size });
      setRows(result.records);
      setTotal(Number(result.total));
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [filters, page]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const handleSearch = (values: LeadFilterValues) => {
    setFilters({
      keyword: values.keyword || undefined,
      status: values.status || undefined,
      source: values.source || undefined,
      ownerId: values.ownerId ?? null,
    });
    setPage((prev) => ({ ...prev, current: 1 }));
  };

  const handleReset = () => {
    form.resetFields();
    setFilters({});
    setPage((prev) => ({ ...prev, current: 1 }));
  };

  const columns: TableProps<LeadView>['columns'] = [
    {
      title: '线索编号',
      dataIndex: 'leadNo',
      width: 160,
      render: (value?: string | null) => value || '-',
    },
    { title: '线索名称', dataIndex: 'name', width: 180, ellipsis: true },
    {
      title: '联系人',
      dataIndex: 'contactName',
      width: 100,
      render: (value?: string | null) => value || '-',
    },
    {
      title: '联系电话',
      dataIndex: 'contactPhone',
      width: 140,
      render: (value?: string | null) => value || '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value?: string | null) => (
        <Tag color={LEAD_STATUS_COLOR[value ?? '']}>
          {LEAD_STATUS_LABEL[value ?? ''] ?? value ?? '-'}
        </Tag>
      ),
    },
    {
      title: '来源',
      dataIndex: 'source',
      width: 100,
      render: (value?: string | null) => value || '-',
    },
    {
      title: '行业',
      dataIndex: 'industry',
      width: 100,
      render: (value?: string | null) => value || '-',
    },
    {
      title: '负责人',
      dataIndex: 'ownerId',
      width: 100,
      render: (value?: number | null) => value ?? '-',
    },
    {
      title: '转化客户',
      dataIndex: 'customerId',
      width: 110,
      render: (value?: number | null) => value ?? '-',
    },
    {
      title: '转化商机',
      dataIndex: 'opportunityId',
      width: 110,
      render: (value?: number | null) => value ?? '-',
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 170,
      render: (value?: string | null) => formatDateTime(value),
    },
    {
      title: '操作',
      key: 'action',
      fixed: 'right',
      width: 280,
      render: (_, row) => {
        const closed = isLeadClosed(row.status);
        const statuses = nextLeadStatuses(row.status);
        return (
          <Space size={0} wrap>
            {hasPermission('sys:crm:lead:update') && !closed && (
              <Button
                size="small"
                type="link"
                onClick={() => {
                  setEditing(row);
                  setFormOpen(true);
                }}
              >
                编辑
              </Button>
            )}
            {hasPermission('sys:crm:lead:update') && statuses.length > 0 && (
              <Button size="small" type="link" onClick={() => setStatusLead(row)}>
                状态
              </Button>
            )}
            {hasPermission('sys:crm:lead:assign') && !closed && (
              <Button size="small" type="link" onClick={() => setAssignTarget(row)}>
                分配
              </Button>
            )}
            {hasPermission('sys:crm:lead:invalid') && !closed && (
              <Button size="small" type="link" danger onClick={() => setInvalidTarget(row)}>
                无效
              </Button>
            )}
            {hasPermission('sys:crm:lead:convert') && !closed && (
              <Button size="small" type="link" onClick={() => setConvertTarget(row)}>
                转化
              </Button>
            )}
            <Button size="small" type="link" onClick={() => setActivityLead(row)}>
              动态
            </Button>
          </Space>
        );
      },
    },
  ];

  return (
    <Card
      title="线索管理"
      extra={
        hasPermission('sys:crm:lead:create') ? (
          <Button
            type="primary"
            onClick={() => {
              setEditing(null);
              setFormOpen(true);
            }}
          >
            新建线索
          </Button>
        ) : null
      }
    >
      <Form
        form={form}
        layout="inline"
        onFinish={handleSearch}
        style={{ marginBottom: 16, rowGap: 12 }}
      >
        <Form.Item name="keyword">
          <Input placeholder="名称 / 编号 / 联系人" allowClear style={{ width: 200 }} />
        </Form.Item>
        <Form.Item name="status">
          <Select
            placeholder="线索状态"
            allowClear
            style={{ width: 130 }}
            options={LEAD_STATUS_OPTIONS}
          />
        </Form.Item>
        <Form.Item name="source">
          <Input placeholder="来源" allowClear style={{ width: 130 }} />
        </Form.Item>
        <Form.Item name="ownerId">
          <InputNumber min={1} precision={0} placeholder="负责人 ID" style={{ width: 130 }} />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit">
              查询
            </Button>
            <Button onClick={handleReset}>重置</Button>
          </Space>
        </Form.Item>
      </Form>
      <Table<LeadView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 1600 }}
        pagination={{
          current: page.current,
          pageSize: page.size,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (current, size) => setPage({ current, size }),
        }}
      />

      <LeadFormModal
        open={formOpen}
        initial={editing}
        onCancel={() => {
          setFormOpen(false);
          setEditing(null);
        }}
        onSaved={() => {
          setFormOpen(false);
          setEditing(null);
          void loadData();
        }}
      />
      <LeadStatusModal
        lead={statusLead}
        onCancel={() => setStatusLead(null)}
        onSaved={() => {
          setStatusLead(null);
          void loadData();
        }}
      />
      <LeadAssignModal
        lead={assignTarget}
        onCancel={() => setAssignTarget(null)}
        onSaved={() => {
          setAssignTarget(null);
          void loadData();
        }}
      />
      <LeadInvalidModal
        lead={invalidTarget}
        onCancel={() => setInvalidTarget(null)}
        onSaved={() => {
          setInvalidTarget(null);
          void loadData();
        }}
      />
      <LeadConvertModal
        lead={convertTarget}
        onCancel={() => setConvertTarget(null)}
        onSaved={() => {
          setConvertTarget(null);
          void loadData();
        }}
      />
      <LeadActivityDrawer
        key={activityLead?.id ?? 'none'}
        lead={activityLead}
        onClose={() => setActivityLead(null)}
        onChanged={() => void loadData()}
      />
      <Typography.Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
        线索联系电话与邮箱按权限脱敏展示；转化时可指定既有客户 ID，留空则自动创建客户。
      </Typography.Text>
    </Card>
  );
}
