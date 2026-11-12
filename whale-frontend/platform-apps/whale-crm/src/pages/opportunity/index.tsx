// 商机管理：列表筛选分页、新建编辑、阶段推进、阶段历史、销售漏斗
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
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
import { useLocation, useNavigate } from 'react-router-dom';
import {
  changeOpportunityStage,
  createOpportunity,
  getOpportunityFunnel,
  listOpportunityStageHistory,
  pageOpportunities,
  updateOpportunity,
  type FunnelStageView,
  type FunnelView,
  type OpportunityPageQuery,
  type OpportunitySaveInput,
  type OpportunityStageHistoryView,
  type OpportunityView,
} from '../../api/crm';
import { hasPermission } from '../../utils/permission';
import {
  OPPORTUNITY_STAGE_COLOR,
  OPPORTUNITY_STAGE_LABEL,
  formatDateTime,
} from '../../utils/dict';

/** 筛选条件 */
interface OpportunityFilterValues {
  keyword?: string;
  customerId?: number | null;
  stage?: string;
  ownerId?: number | null;
  expectedCloseFrom?: string;
  expectedCloseTo?: string;
}

/** 新建 / 编辑表单值 */
interface OpportunityFormValues {
  customerId?: number | null;
  name: string;
  amount?: number | null;
  expectedCloseDate?: string;
  source?: string;
  competitor?: string;
  nextActionAt?: string;
  ownerId?: number | null;
  deptId?: number | null;
  probability?: number | null;
}

/** 阶段推进表单值 */
interface StageFormValues {
  stage: string;
  lostReason?: string;
  amount?: number | null;
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
 * 格式化金额。
 *
 * @param value 后端金额
 * @returns 千分位金额文本
 */
function formatAmount(value?: number | string | null): string {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  const num = Number(value);
  return Number.isNaN(num)
    ? String(value)
    : num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/**
 * 商机是否已结束。
 *
 * @param stage 商机阶段
 * @returns 是否终态
 */
function isOpportunityClosed(stage?: string | null): boolean {
  return stage === 'WON' || stage === 'LOST';
}

/**
 * 计算允许推进的阶段。
 *
 * @param stage 当前阶段
 * @returns 可流转阶段
 */
function nextStageOptions(stage?: string | null): { value: string; label: string }[] {
  switch (stage) {
    case 'CONTACT':
      return [
        { value: 'PROPOSAL', label: OPPORTUNITY_STAGE_LABEL.PROPOSAL },
        { value: 'LOST', label: OPPORTUNITY_STAGE_LABEL.LOST },
      ];
    case 'PROPOSAL':
      return [
        { value: 'NEGOTIATION', label: OPPORTUNITY_STAGE_LABEL.NEGOTIATION },
        { value: 'LOST', label: OPPORTUNITY_STAGE_LABEL.LOST },
      ];
    case 'NEGOTIATION':
      return [
        { value: 'WON', label: OPPORTUNITY_STAGE_LABEL.WON },
        { value: 'LOST', label: OPPORTUNITY_STAGE_LABEL.LOST },
      ];
    default:
      return [];
  }
}

interface OpportunityFormModalProps {
  open: boolean;
  initial: OpportunityView | null;
  onCancel: () => void;
  onSaved: () => void;
}

/** 商机新建 / 编辑弹窗 */
function OpportunityFormModal({ open, initial, onCancel, onSaved }: OpportunityFormModalProps) {
  const [form] = Form.useForm<OpportunityFormValues>();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    if (initial) {
      form.setFieldsValue({
        customerId: initial.customerId ?? null,
        name: initial.name,
        amount: initial.amount === null || initial.amount === undefined ? null : Number(initial.amount),
        expectedCloseDate: initial.expectedCloseDate ?? undefined,
        source: initial.source ?? undefined,
        competitor: initial.competitor ?? undefined,
        nextActionAt: initial.nextActionAt ?? undefined,
        ownerId: initial.ownerId ?? null,
        deptId: initial.deptId ?? null,
        probability:
          initial.probability === null || initial.probability === undefined
            ? null
            : Number(initial.probability),
      });
    } else {
      form.resetFields();
    }
  }, [open, initial, form]);

  const handleOk = async () => {
    setSaving(true);
    try {
      const values = await form.validateFields();
      const payload: OpportunitySaveInput = {
        customerId: values.customerId ?? null,
        name: values.name,
        amount: values.amount ?? null,
        expectedCloseDate: values.expectedCloseDate || undefined,
        source: values.source,
        competitor: values.competitor,
        nextActionAt: values.nextActionAt || undefined,
        ownerId: values.ownerId ?? null,
        deptId: values.deptId ?? null,
        probability: values.probability ?? null,
      };
      if (initial) {
        await updateOpportunity(initial.id, payload);
        message.success('商机已更新');
      } else {
        await createOpportunity(payload);
        message.success('商机已创建');
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
      title={initial ? `编辑商机：${initial.name}` : '新建商机'}
      width={720}
      confirmLoading={saving}
      okText="保存"
      cancelText="取消"
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item
            name="customerId"
            label="客户 ID"
            rules={initial ? [] : [{ required: true, message: '请输入客户 ID' }]}
            style={{ flex: 1 }}
          >
            <InputNumber min={1} precision={0} style={{ width: '100%' }} disabled={Boolean(initial)} />
          </Form.Item>
          <Form.Item
            name="name"
            label="商机名称"
            rules={[{ required: true, message: '请输入商机名称' }]}
            style={{ flex: 2 }}
          >
            <Input maxLength={100} />
          </Form.Item>
        </Space>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="amount" label="商机金额" style={{ flex: 1 }}>
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="expectedCloseDate" label="预计关闭日期" style={{ flex: 1 }}>
            <Input placeholder="yyyy-MM-dd" />
          </Form.Item>
          <Form.Item name="probability" label="赢单概率(%)" style={{ flex: 1 }}>
            <InputNumber min={0} max={100} precision={0} style={{ width: '100%' }} />
          </Form.Item>
        </Space>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="source" label="来源" style={{ flex: 1 }}>
            <Input maxLength={50} />
          </Form.Item>
          <Form.Item name="competitor" label="竞争对手" style={{ flex: 1 }}>
            <Input maxLength={100} />
          </Form.Item>
        </Space>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="nextActionAt" label="下次行动时间" style={{ flex: 1 }}>
            <Input placeholder="yyyy-MM-dd HH:mm:ss" />
          </Form.Item>
          <Form.Item name="ownerId" label="负责人 ID" style={{ flex: 1 }}>
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="deptId" label="部门 ID" style={{ flex: 1 }}>
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
        </Space>
      </Form>
    </Modal>
  );
}

interface StageModalProps {
  opportunity: OpportunityView | null;
  onCancel: () => void;
  onSaved: () => void;
}

/** 商机阶段推进弹窗 */
function StageModal({ opportunity, onCancel, onSaved }: StageModalProps) {
  const [form] = Form.useForm<StageFormValues>();
  const [saving, setSaving] = useState(false);
  const stageValue = Form.useWatch('stage', form);

  useEffect(() => {
    if (opportunity) {
      const options = nextStageOptions(opportunity.stage);
      form.setFieldsValue({
        stage: options[0]?.value,
        lostReason: undefined,
        amount:
          opportunity.amount === null || opportunity.amount === undefined
            ? null
            : Number(opportunity.amount),
      });
    }
  }, [opportunity, form]);

  const handleOk = async () => {
    if (!opportunity) {
      return;
    }
    setSaving(true);
    try {
      const values = await form.validateFields();
      if (values.stage === 'WON' && (values.amount === null || values.amount === undefined || values.amount <= 0)) {
        message.error('转为赢单需填写大于 0 的商机金额');
        return;
      }
      if (values.stage === 'LOST' && !values.lostReason?.trim()) {
        message.error('转为输单需填写输单原因');
        return;
      }
      await changeOpportunityStage(opportunity.id, {
        stage: values.stage,
        lostReason: values.lostReason?.trim(),
        amount: values.amount ?? null,
      });
      message.success('商机阶段已推进');
      onSaved();
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={Boolean(opportunity)}
      title={opportunity ? `阶段推进：${opportunity.name}` : '阶段推进'}
      confirmLoading={saving}
      okText="确认推进"
      cancelText="取消"
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item
          name="stage"
          label="目标阶段"
          rules={[{ required: true, message: '请选择目标阶段' }]}
        >
          <Select options={nextStageOptions(opportunity?.stage)} />
        </Form.Item>
        <Form.Item
          name="amount"
          label="商机金额"
          extra={stageValue === 'WON' ? '转为赢单时金额必须大于 0' : '可留空，默认使用原商机金额'}
        >
          <InputNumber min={0} precision={2} style={{ width: '100%' }} />
        </Form.Item>
        {stageValue === 'LOST' && (
          <Form.Item
            name="lostReason"
            label="输单原因"
            rules={[{ required: true, message: '请填写输单原因' }]}
          >
            <Input.TextArea rows={3} maxLength={200} showCount />
          </Form.Item>
        )}
      </Form>
    </Modal>
  );
}

interface StageHistoryDrawerProps {
  opportunity: OpportunityView | null;
  onClose: () => void;
}

/** 商机阶段历史抽屉 */
function StageHistoryDrawer({ opportunity, onClose }: StageHistoryDrawerProps) {
  const [rows, setRows] = useState<OpportunityStageHistoryView[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!opportunity) {
      return;
    }
    let active = true;
    setLoading(true);
    listOpportunityStageHistory(opportunity.id)
      .then((records) => {
        if (active) {
          setRows(records);
        }
      })
      .catch((error: unknown) => {
        message.error(errorText(error));
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [opportunity]);

  const columns: TableProps<OpportunityStageHistoryView>['columns'] = [
    {
      title: '原阶段',
      dataIndex: 'fromStage',
      width: 100,
      render: (value?: string | null) => OPPORTUNITY_STAGE_LABEL[value ?? ''] ?? value ?? '-',
    },
    {
      title: '新阶段',
      dataIndex: 'toStage',
      width: 100,
      render: (value?: string | null) => (
        <Tag color={OPPORTUNITY_STAGE_COLOR[value ?? '']}>
          {OPPORTUNITY_STAGE_LABEL[value ?? ''] ?? value ?? '-'}
        </Tag>
      ),
    },
    {
      title: '金额',
      dataIndex: 'amount',
      width: 140,
      align: 'right',
      render: (value?: number | string | null) => formatAmount(value),
    },
    {
      title: '概率',
      dataIndex: 'probability',
      width: 90,
      render: (value?: number | string | null) => (value === null || value === undefined ? '-' : `${value}%`),
    },
    {
      title: '输单原因',
      dataIndex: 'lostReason',
      ellipsis: true,
      render: (value?: string | null) => value || '-',
    },
    { title: '操作人', dataIndex: 'operatorId', width: 100, render: (value?: number | null) => value ?? '-' },
    {
      title: '变更时间',
      dataIndex: 'changedAt',
      width: 170,
      render: (value?: string | null) => formatDateTime(value),
    },
  ];

  return (
    <Drawer
      open={Boolean(opportunity)}
      title={opportunity ? `阶段历史：${opportunity.name}` : '阶段历史'}
      width={760}
      onClose={onClose}
    >
      <Table<OpportunityStageHistoryView>
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={false}
      />
    </Drawer>
  );
}

/** 商机漏斗筛选条件 */
interface FunnelFilterValues {
  ownerId?: number | null;
  expectedCloseFrom?: string;
  expectedCloseTo?: string;
}

/** 商机漏斗 Tab */
function OpportunityFunnelTab() {
  const [form] = Form.useForm<FunnelFilterValues>();
  const [data, setData] = useState<FunnelView | null>(null);
  const [filters, setFilters] = useState<FunnelFilterValues>({});
  const [loading, setLoading] = useState(false);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await getOpportunityFunnel({
        ownerId: filters.ownerId ?? null,
        expectedCloseFrom: filters.expectedCloseFrom,
        expectedCloseTo: filters.expectedCloseTo,
      });
      setData(result);
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [filters]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const handleSearch = (values: FunnelFilterValues) => {
    setFilters({
      ownerId: values.ownerId ?? null,
      expectedCloseFrom: values.expectedCloseFrom || undefined,
      expectedCloseTo: values.expectedCloseTo || undefined,
    });
  };

  const handleReset = () => {
    form.resetFields();
    setFilters({});
  };

  const columns: TableProps<FunnelStageView>['columns'] = [
    {
      title: '阶段',
      dataIndex: 'stage',
      width: 140,
      render: (value?: string | null) => (
        <Tag color={OPPORTUNITY_STAGE_COLOR[value ?? '']}>
          {OPPORTUNITY_STAGE_LABEL[value ?? ''] ?? value ?? '-'}
        </Tag>
      ),
    },
    { title: '商机数量', dataIndex: 'count', width: 120, align: 'right' },
    {
      title: '商机金额',
      dataIndex: 'amount',
      width: 180,
      align: 'right',
      render: (value?: number | string | null) => formatAmount(value),
    },
    {
      title: '较上一阶段转化率',
      dataIndex: 'conversionRate',
      width: 170,
      align: 'right',
      render: (value?: number | string | null) =>
        value === null || value === undefined ? '-' : `${value}%`,
    },
  ];

  return (
    <>
      <Form form={form} layout="inline" onFinish={handleSearch} style={{ marginBottom: 16, rowGap: 12 }}>
        <Form.Item name="ownerId">
          <InputNumber min={1} precision={0} placeholder="负责人 ID" style={{ width: 140 }} />
        </Form.Item>
        <Form.Item name="expectedCloseFrom">
          <Input placeholder="预计关闭起（yyyy-MM-dd）" style={{ width: 210 }} />
        </Form.Item>
        <Form.Item name="expectedCloseTo">
          <Input placeholder="预计关闭止（yyyy-MM-dd）" style={{ width: 210 }} />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit" loading={loading}>
              查询
            </Button>
            <Button onClick={handleReset}>重置</Button>
          </Space>
        </Form.Item>
      </Form>
      <Space size={24} style={{ display: 'flex', marginBottom: 16 }} wrap>
        <Statistic title="在途商机数" value={data?.totalCount ?? 0} />
        <Statistic title="在途商机金额" value={formatAmount(data?.totalAmount)} />
        <Statistic title="输单数" value={data?.lostCount ?? 0} />
        <Statistic title="输单金额" value={formatAmount(data?.lostAmount)} />
      </Space>
      <Table<FunnelStageView>
        rowKey="stage"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={data?.stages ?? []}
        pagination={false}
      />
    </>
  );
}

/** 商机列表 Tab */
function OpportunityListTab() {
  const [form] = Form.useForm<OpportunityFilterValues>();
  const [rows, setRows] = useState<OpportunityView[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState<OpportunityPageQuery>({});
  const [page, setPage] = useState({ current: 1, size: 10 });
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<OpportunityView | null>(null);
  const [stageTarget, setStageTarget] = useState<OpportunityView | null>(null);
  const [historyTarget, setHistoryTarget] = useState<OpportunityView | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageOpportunities({ ...filters, current: page.current, size: page.size });
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

  const handleSearch = (values: OpportunityFilterValues) => {
    setFilters({
      keyword: values.keyword || undefined,
      customerId: values.customerId ?? null,
      stage: values.stage || undefined,
      ownerId: values.ownerId ?? null,
      expectedCloseFrom: values.expectedCloseFrom || undefined,
      expectedCloseTo: values.expectedCloseTo || undefined,
    });
    setPage((prev) => ({ ...prev, current: 1 }));
  };

  const handleReset = () => {
    form.resetFields();
    setFilters({});
    setPage((prev) => ({ ...prev, current: 1 }));
  };

  const columns: TableProps<OpportunityView>['columns'] = [
    { title: '商机名称', dataIndex: 'name', width: 180, ellipsis: true },
    { title: '客户 ID', dataIndex: 'customerId', width: 110, render: (value?: number | null) => value ?? '-' },
    {
      title: '金额',
      dataIndex: 'amount',
      width: 140,
      align: 'right',
      render: (value?: number | string | null) => formatAmount(value),
    },
    {
      title: '阶段',
      dataIndex: 'stage',
      width: 100,
      render: (value?: string | null) => (
        <Tag color={OPPORTUNITY_STAGE_COLOR[value ?? '']}>
          {OPPORTUNITY_STAGE_LABEL[value ?? ''] ?? value ?? '-'}
        </Tag>
      ),
    },
    {
      title: '概率',
      dataIndex: 'probability',
      width: 90,
      render: (value?: number | string | null) =>
        value === null || value === undefined ? '-' : `${value}%`,
    },
    {
      title: '预计关闭',
      dataIndex: 'expectedCloseDate',
      width: 120,
      render: (value?: string | null) => value || '-',
    },
    { title: '负责人', dataIndex: 'ownerId', width: 100, render: (value?: number | null) => value ?? '-' },
    {
      title: '下次行动',
      dataIndex: 'nextActionAt',
      width: 170,
      render: (value?: string | null) => formatDateTime(value),
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
      width: 200,
      render: (_, row) => {
        const closed = isOpportunityClosed(row.stage);
        const stageOptions = nextStageOptions(row.stage);
        return (
          <Space size={0} wrap>
            {hasPermission('sys:crm:opportunity:update') && !closed && (
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
            {hasPermission('sys:crm:opportunity:stage') && stageOptions.length > 0 && (
              <Button size="small" type="link" onClick={() => setStageTarget(row)}>
                阶段
              </Button>
            )}
            <Button size="small" type="link" onClick={() => setHistoryTarget(row)}>
              历史
            </Button>
          </Space>
        );
      },
    },
  ];

  return (
    <>
      <Space style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}>
        {hasPermission('sys:crm:opportunity:create') && (
          <Button
            type="primary"
            onClick={() => {
              setEditing(null);
              setFormOpen(true);
            }}
          >
            新建商机
          </Button>
        )}
      </Space>
      <Form form={form} layout="inline" onFinish={handleSearch} style={{ marginBottom: 16, rowGap: 12 }}>
        <Form.Item name="keyword">
          <Input placeholder="商机名称" allowClear style={{ width: 180 }} />
        </Form.Item>
        <Form.Item name="customerId">
          <InputNumber min={1} precision={0} placeholder="客户 ID" style={{ width: 130 }} />
        </Form.Item>
        <Form.Item name="stage">
          <Select
            placeholder="阶段"
            allowClear
            style={{ width: 130 }}
            options={[
              { value: 'CONTACT', label: '接洽' },
              { value: 'PROPOSAL', label: '方案' },
              { value: 'NEGOTIATION', label: '谈判' },
              { value: 'WON', label: '赢单' },
              { value: 'LOST', label: '输单' },
            ]}
          />
        </Form.Item>
        <Form.Item name="ownerId">
          <InputNumber min={1} precision={0} placeholder="负责人 ID" style={{ width: 130 }} />
        </Form.Item>
        <Form.Item name="expectedCloseFrom">
          <Input placeholder="关闭起（yyyy-MM-dd）" style={{ width: 190 }} />
        </Form.Item>
        <Form.Item name="expectedCloseTo">
          <Input placeholder="关闭止（yyyy-MM-dd）" style={{ width: 190 }} />
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
      <Table<OpportunityView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 1500 }}
        pagination={{
          current: page.current,
          pageSize: page.size,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (current, size) => setPage({ current, size }),
        }}
      />

      <OpportunityFormModal
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
      <StageModal
        opportunity={stageTarget}
        onCancel={() => setStageTarget(null)}
        onSaved={() => {
          setStageTarget(null);
          void loadData();
        }}
      />
      <StageHistoryDrawer opportunity={historyTarget} onClose={() => setHistoryTarget(null)} />
    </>
  );
}

/** 商机管理页 */
export default function OpportunityPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const activeKey = location.pathname.endsWith('/funnel') ? 'funnel' : 'list';

  return (
    <Card title="商机管理">
      <Tabs
        activeKey={activeKey}
        onChange={(key) => navigate(key === 'funnel' ? '/opportunity/funnel' : '/opportunity')}
        items={[
          { key: 'list', label: '商机列表', children: <OpportunityListTab /> },
          { key: 'funnel', label: '销售漏斗', children: <OpportunityFunnelTab /> },
        ]}
      />
      <Typography.Text type="secondary">
        阶段按「接洽 → 方案 → 谈判 → 赢单」顺序推进，任意在途阶段可标记输单。
      </Typography.Text>
    </Card>
  );
}
