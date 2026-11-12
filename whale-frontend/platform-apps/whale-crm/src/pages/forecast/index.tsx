// 销售预测与目标：预测快照生成 / 调整 / 调整记录，目标新建 / 调整 / 删除 / 完成度
import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Progress,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import {
  adjustForecast,
  createSalesTarget,
  deleteSalesTarget,
  generateForecast,
  getSalesTargetCompletion,
  listForecastAdjustments,
  listSalesTargetAdjustments,
  pageForecasts,
  pageSalesTargets,
  updateSalesTarget,
  type ForecastAdjustmentView,
  type ForecastPageQuery,
  type ForecastSnapshotView,
  type SalesTargetAdjustmentView,
  type SalesTargetPageQuery,
  type SalesTargetSaveInput,
  type SalesTargetUpdateInput,
  type SalesTargetView,
  type TargetCompletionView,
} from '../../api/crm';
import { hasPermission } from '../../utils/permission';
import {
  FORECAST_FIELD_LABEL,
  FORECAST_FIELD_OPTIONS,
  FORECAST_PERIOD_TYPE_LABEL,
  FORECAST_PERIOD_TYPE_OPTIONS,
  FORECAST_SCOPE_TYPE_OPTIONS,
  formatDateTime,
} from '../../utils/dict';

/** 预测 / 目标筛选条件 */
interface PeriodFilterValues {
  period?: string;
  periodType?: string;
  scopeType?: string;
  ownerId?: number | null;
  deptId?: number | null;
}

/** 生成预测表单值 */
interface GenerateFormValues {
  period: string;
  periodType: string;
  scopeType: string;
  ownerId?: number | null;
  deptId?: number | null;
}

/** 预测调整表单值 */
interface ForecastAdjustFormValues {
  fieldCode: string;
  amount: number;
  reason: string;
}

/** 新建目标表单值 */
interface TargetFormValues {
  period: string;
  periodType: string;
  scopeType: string;
  ownerId?: number | null;
  deptId?: number | null;
  targetAmount: number;
  remark?: string;
}

/** 编辑目标表单值 */
interface TargetUpdateFormValues {
  targetAmount: number;
  reason?: string;
  remark?: string;
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
 * 生成本期默认值。
 *
 * @returns yyyy-MM 周期
 */
function currentPeriod(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

/**
 * 周期范围描述。
 *
 * @param scopeType 范围类型
 * @param ownerId 负责人
 * @param deptId 部门
 * @returns 描述文本
 */
function scopeText(
  scopeType?: string | null,
  ownerId?: number | null,
  deptId?: number | null,
): string {
  if (scopeType === 'DEPT') {
    return `部门 ${deptId ?? '-'}`;
  }
  return `负责人 ${ownerId ?? '-'}`;
}

/** 预测调整记录抽屉 */
function ForecastAdjustmentDrawer({
  snapshot,
  onClose,
}: {
  snapshot: ForecastSnapshotView | null;
  onClose: () => void;
}) {
  const [rows, setRows] = useState<ForecastAdjustmentView[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!snapshot) {
      setRows([]);
      return;
    }
    let active = true;
    setLoading(true);
    listForecastAdjustments(snapshot.id)
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
  }, [snapshot]);

  const columns: TableProps<ForecastAdjustmentView>['columns'] = [
    {
      title: '调整字段',
      dataIndex: 'fieldCode',
      width: 120,
      render: (value?: string | null) => FORECAST_FIELD_LABEL[value ?? ''] ?? value ?? '-',
    },
    {
      title: '调整前',
      dataIndex: 'beforeAmount',
      width: 140,
      align: 'right',
      render: (value?: number | string | null) => formatAmount(value),
    },
    {
      title: '调整后',
      dataIndex: 'afterAmount',
      width: 140,
      align: 'right',
      render: (value?: number | string | null) => formatAmount(value),
    },
    { title: '原因', dataIndex: 'reason', ellipsis: true, render: (value?: string | null) => value || '-' },
    { title: '操作人', dataIndex: 'operatorId', width: 100, render: (value?: number | null) => value ?? '-' },
    {
      title: '调整时间',
      dataIndex: 'adjustedAt',
      width: 170,
      render: (value?: string | null) => formatDateTime(value),
    },
  ];

  return (
    <Drawer
      open={Boolean(snapshot)}
      title={snapshot ? `预测调整记录：${snapshot.period ?? '-'}` : '预测调整记录'}
      width={760}
      onClose={onClose}
    >
      <Table<ForecastAdjustmentView>
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

/** 生成预测弹窗 */
function GenerateModal({
  open,
  onCancel,
  onSaved,
}: {
  open: boolean;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<GenerateFormValues>();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (open) {
      form.resetFields();
      form.setFieldsValue({ period: currentPeriod(), periodType: 'MONTH', scopeType: 'USER' });
    }
  }, [open, form]);

  const handleOk = async () => {
    setSaving(true);
    try {
      const values = await form.validateFields();
      const result = await generateForecast({
        period: values.period,
        periodType: values.periodType,
        scopeType: values.scopeType,
        ownerId: values.ownerId ?? null,
        deptId: values.deptId ?? null,
      });
      message.success(`预测已生成：${result.period ?? values.period}`);
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
      title="生成销售预测"
      confirmLoading={saving}
      okText="生成"
      cancelText="取消"
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item
          name="period"
          label="预测周期"
          rules={[{ required: true, message: '请输入预测周期' }]}
          extra="月度格式 yyyy-MM（如 2026-11），季度格式 yyyy-Qn（如 2026-Q4）"
        >
          <Input placeholder="yyyy-MM 或 yyyy-Qn" maxLength={10} />
        </Form.Item>
        <Form.Item name="periodType" label="周期类型" rules={[{ required: true }]}>
          <Select options={FORECAST_PERIOD_TYPE_OPTIONS} />
        </Form.Item>
        <Form.Item name="scopeType" label="统计范围" rules={[{ required: true }]}>
          <Select options={FORECAST_SCOPE_TYPE_OPTIONS} />
        </Form.Item>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="ownerId" label="负责人 ID" style={{ flex: 1 }} extra="范围=按负责人时填写">
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="deptId" label="部门 ID" style={{ flex: 1 }} extra="范围=按部门时填写">
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
        </Space>
      </Form>
    </Modal>
  );
}

/** 预测调整弹窗 */
function ForecastAdjustModal({
  snapshot,
  onCancel,
  onSaved,
}: {
  snapshot: ForecastSnapshotView | null;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<ForecastAdjustFormValues>();
  const [saving, setSaving] = useState(false);
  const fieldValue = Form.useWatch('fieldCode', form);

  useEffect(() => {
    if (!snapshot) {
      return;
    }
    form.resetFields();
    form.setFieldsValue({
      fieldCode: 'BEST',
      amount:
        snapshot.bestAmount === null || snapshot.bestAmount === undefined
          ? 0
          : Number(snapshot.bestAmount),
      reason: '',
    });
  }, [snapshot, form]);

  const handleFieldChange = (value: string) => {
    if (!snapshot) {
      return;
    }
    const amounts: Record<string, number | string | null | undefined> = {
      PIPELINE: snapshot.pipelineAmount,
      BEST: snapshot.bestAmount,
      COMMIT: snapshot.commitAmount,
    };
    const current = amounts[value];
    form.setFieldsValue({ amount: current === null || current === undefined ? 0 : Number(current) });
  };

  const handleOk = async () => {
    if (!snapshot) {
      return;
    }
    setSaving(true);
    try {
      const values = await form.validateFields();
      await adjustForecast(snapshot.id, {
        fieldCode: values.fieldCode,
        amount: values.amount,
        reason: values.reason,
      });
      message.success('预测已调整');
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
      open={Boolean(snapshot)}
      title={snapshot ? `调整预测：${snapshot.period ?? '-'}` : '调整预测'}
      confirmLoading={saving}
      okText="保存"
      cancelText="取消"
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item name="fieldCode" label="调整字段" rules={[{ required: true, message: '请选择调整字段' }]}>
          <Select options={FORECAST_FIELD_OPTIONS} onChange={handleFieldChange} />
        </Form.Item>
        <Form.Item
          name="amount"
          label="调整后金额"
          rules={[{ required: true, message: '请输入调整后金额' }]}
          extra={`当前字段：${FORECAST_FIELD_LABEL[fieldValue ?? ''] ?? '-'}`}
        >
          <InputNumber min={0} precision={2} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item
          name="reason"
          label="调整原因"
          rules={[{ required: true, message: '请输入调整原因' }]}
        >
          <Input.TextArea rows={3} maxLength={500} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** 销售预测 Tab */
function ForecastTab() {
  const [form] = Form.useForm<PeriodFilterValues>();
  const [rows, setRows] = useState<ForecastSnapshotView[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState<ForecastPageQuery>({});
  const [page, setPage] = useState({ current: 1, size: 10 });
  const [generateOpen, setGenerateOpen] = useState(false);
  const [adjustTarget, setAdjustTarget] = useState<ForecastSnapshotView | null>(null);
  const [historyTarget, setHistoryTarget] = useState<ForecastSnapshotView | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageForecasts({ ...filters, current: page.current, size: page.size });
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

  const handleSearch = (values: PeriodFilterValues) => {
    setFilters({
      period: values.period || undefined,
      periodType: values.periodType || undefined,
      scopeType: values.scopeType || undefined,
      ownerId: values.ownerId ?? null,
      deptId: values.deptId ?? null,
    });
    setPage((prev) => ({ ...prev, current: 1 }));
  };

  const handleReset = () => {
    form.resetFields();
    setFilters({});
    setPage((prev) => ({ ...prev, current: 1 }));
  };

  const columns: TableProps<ForecastSnapshotView>['columns'] = [
    { title: '周期', dataIndex: 'period', width: 110, render: (value?: string | null) => value || '-' },
    {
      title: '类型',
      dataIndex: 'periodType',
      width: 80,
      render: (value?: string | null) => FORECAST_PERIOD_TYPE_LABEL[value ?? ''] ?? value ?? '-',
    },
    {
      title: '范围',
      dataIndex: 'scopeType',
      width: 140,
      render: (_: unknown, row) => scopeText(row.scopeType, row.ownerId, row.deptId),
    },
    {
      title: '管道金额',
      dataIndex: 'pipelineAmount',
      width: 140,
      align: 'right',
      render: (value?: number | string | null) => formatAmount(value),
    },
    {
      title: '最佳金额',
      dataIndex: 'bestAmount',
      width: 140,
      align: 'right',
      render: (value?: number | string | null) => formatAmount(value),
    },
    {
      title: '承诺金额',
      dataIndex: 'commitAmount',
      width: 140,
      align: 'right',
      render: (value?: number | string | null) => formatAmount(value),
    },
    {
      title: '加权金额',
      dataIndex: 'weightedAmount',
      width: 140,
      align: 'right',
      render: (value?: number | string | null) => formatAmount(value),
    },
    {
      title: '商机数',
      dataIndex: 'opportunityCount',
      width: 90,
      align: 'right',
      render: (value?: number | null) => value ?? 0,
    },
    {
      title: '手工调整',
      dataIndex: 'manualAdjusted',
      width: 100,
      render: (value?: boolean | null) =>
        value ? <Tag color="orange">已调整</Tag> : <Tag>未调整</Tag>,
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 170,
      render: (value?: string | null) => formatDateTime(value),
    },
    {
      title: '操作',
      key: 'action',
      fixed: 'right',
      width: 180,
      render: (_, row) => (
        <Space size={0} wrap>
          {hasPermission('sys:crm:forecast:manage') && (
            <Button size="small" type="link" onClick={() => setAdjustTarget(row)}>
              调整
            </Button>
          )}
          <Button size="small" type="link" onClick={() => setHistoryTarget(row)}>
            调整记录
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Space style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}>
        {hasPermission('sys:crm:forecast:manage') && (
          <Button type="primary" onClick={() => setGenerateOpen(true)}>
            生成预测
          </Button>
        )}
      </Space>
      <Form form={form} layout="inline" onFinish={handleSearch} style={{ marginBottom: 16, rowGap: 12 }}>
        <Form.Item name="period">
          <Input placeholder="周期 yyyy-MM / yyyy-Qn" allowClear style={{ width: 200 }} />
        </Form.Item>
        <Form.Item name="periodType">
          <Select placeholder="周期类型" allowClear style={{ width: 120 }} options={FORECAST_PERIOD_TYPE_OPTIONS} />
        </Form.Item>
        <Form.Item name="scopeType">
          <Select placeholder="统计范围" allowClear style={{ width: 120 }} options={FORECAST_SCOPE_TYPE_OPTIONS} />
        </Form.Item>
        <Form.Item name="ownerId">
          <InputNumber min={1} precision={0} placeholder="负责人 ID" style={{ width: 130 }} />
        </Form.Item>
        <Form.Item name="deptId">
          <InputNumber min={1} precision={0} placeholder="部门 ID" style={{ width: 130 }} />
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
      <Table<ForecastSnapshotView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 1450 }}
        pagination={{
          current: page.current,
          pageSize: page.size,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (current, size) => setPage({ current, size }),
        }}
      />

      <GenerateModal
        open={generateOpen}
        onCancel={() => setGenerateOpen(false)}
        onSaved={() => {
          setGenerateOpen(false);
          void loadData();
        }}
      />
      <ForecastAdjustModal
        snapshot={adjustTarget}
        onCancel={() => setAdjustTarget(null)}
        onSaved={() => {
          setAdjustTarget(null);
          void loadData();
        }}
      />
      <ForecastAdjustmentDrawer snapshot={historyTarget} onClose={() => setHistoryTarget(null)} />
    </>
  );
}

/** 目标完成度与调整记录抽屉 */
function TargetDetailDrawer({
  target,
  onClose,
}: {
  target: SalesTargetView | null;
  onClose: () => void;
}) {
  const [completion, setCompletion] = useState<TargetCompletionView | null>(null);
  const [adjustments, setAdjustments] = useState<SalesTargetAdjustmentView[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!target) {
      setCompletion(null);
      setAdjustments([]);
      return;
    }
    let active = true;
    setLoading(true);
    Promise.all([getSalesTargetCompletion(target.id), listSalesTargetAdjustments(target.id)])
      .then(([completionData, adjustmentData]) => {
        if (active) {
          setCompletion(completionData);
          setAdjustments(adjustmentData);
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
  }, [target]);

  const percent = completion?.completionRate ?? 0;
  const percentNumber = Math.min(100, Math.max(0, Number(percent) || 0));

  const columns: TableProps<SalesTargetAdjustmentView>['columns'] = [
    {
      title: '调整前',
      dataIndex: 'beforeAmount',
      width: 140,
      align: 'right',
      render: (value?: number | string | null) => formatAmount(value),
    },
    {
      title: '调整后',
      dataIndex: 'afterAmount',
      width: 140,
      align: 'right',
      render: (value?: number | string | null) => formatAmount(value),
    },
    { title: '原因', dataIndex: 'reason', ellipsis: true, render: (value?: string | null) => value || '-' },
    { title: '操作人', dataIndex: 'operatorId', width: 100, render: (value?: number | null) => value ?? '-' },
    {
      title: '调整时间',
      dataIndex: 'adjustedAt',
      width: 170,
      render: (value?: string | null) => formatDateTime(value),
    },
  ];

  return (
    <Drawer
      open={Boolean(target)}
      title={target ? `目标完成度：${target.period ?? '-'}` : '目标完成度'}
      width={800}
      onClose={onClose}
    >
      {target && (
        <>
          <Descriptions bordered size="small" column={2} style={{ marginBottom: 16 }}>
            <Descriptions.Item label="周期">{completion?.period ?? target.period ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="周期类型">
              {FORECAST_PERIOD_TYPE_LABEL[target.periodType ?? ''] ?? target.periodType ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="统计范围" span={2}>
              {scopeText(target.scopeType, target.ownerId, target.deptId)}
            </Descriptions.Item>
            <Descriptions.Item label="目标金额">
              {formatAmount(completion?.targetAmount ?? target.targetAmount)}
            </Descriptions.Item>
            <Descriptions.Item label="已达成金额">
              {formatAmount(completion?.achievedAmount)}
            </Descriptions.Item>
            <Descriptions.Item label="达成商机数">{completion?.achievedCount ?? 0}</Descriptions.Item>
            <Descriptions.Item label="完成率">
              {completion?.completionRate === null || completion?.completionRate === undefined
                ? '-'
                : `${completion.completionRate}%`}
            </Descriptions.Item>
          </Descriptions>
          <Progress percent={percentNumber} status={percentNumber >= 100 ? 'success' : 'active'} />
          <Typography.Text strong style={{ display: 'block', margin: '16px 0 8px' }}>
            调整记录
          </Typography.Text>
          <Table<SalesTargetAdjustmentView>
            rowKey="id"
            size="small"
            loading={loading}
            columns={columns}
            dataSource={adjustments}
            pagination={false}
          />
        </>
      )}
    </Drawer>
  );
}

/** 新建目标弹窗 */
function TargetCreateModal({
  open,
  onCancel,
  onSaved,
}: {
  open: boolean;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<TargetFormValues>();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (open) {
      form.resetFields();
      form.setFieldsValue({
        period: currentPeriod(),
        periodType: 'MONTH',
        scopeType: 'USER',
        targetAmount: 0,
      });
    }
  }, [open, form]);

  const handleOk = async () => {
    setSaving(true);
    try {
      const values = await form.validateFields();
      const payload: SalesTargetSaveInput = {
        period: values.period,
        periodType: values.periodType,
        scopeType: values.scopeType,
        ownerId: values.ownerId ?? null,
        deptId: values.deptId ?? null,
        targetAmount: values.targetAmount,
        remark: values.remark,
      };
      await createSalesTarget(payload);
      message.success('销售目标已创建');
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
      title="新建销售目标"
      confirmLoading={saving}
      okText="保存"
      cancelText="取消"
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item
          name="period"
          label="目标周期"
          rules={[{ required: true, message: '请输入目标周期' }]}
          extra="月度格式 yyyy-MM（如 2026-11），季度格式 yyyy-Qn（如 2026-Q4）"
        >
          <Input placeholder="yyyy-MM 或 yyyy-Qn" maxLength={10} />
        </Form.Item>
        <Form.Item name="periodType" label="周期类型" rules={[{ required: true }]}>
          <Select options={FORECAST_PERIOD_TYPE_OPTIONS} />
        </Form.Item>
        <Form.Item name="scopeType" label="统计范围" rules={[{ required: true }]}>
          <Select options={FORECAST_SCOPE_TYPE_OPTIONS} />
        </Form.Item>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="ownerId" label="负责人 ID" style={{ flex: 1 }} extra="范围=按负责人时填写">
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="deptId" label="部门 ID" style={{ flex: 1 }} extra="范围=按部门时填写">
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
        </Space>
        <Form.Item
          name="targetAmount"
          label="目标金额"
          rules={[{ required: true, message: '请输入目标金额' }]}
        >
          <InputNumber min={0} precision={2} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={500} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** 编辑 / 调整目标弹窗 */
function TargetUpdateModal({
  target,
  onCancel,
  onSaved,
}: {
  target: SalesTargetView | null;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<TargetUpdateFormValues>();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (target) {
      form.resetFields();
      form.setFieldsValue({
        targetAmount:
          target.targetAmount === null || target.targetAmount === undefined
            ? 0
            : Number(target.targetAmount),
        reason: '',
        remark: target.remark ?? undefined,
      });
    }
  }, [target, form]);

  const handleOk = async () => {
    if (!target) {
      return;
    }
    setSaving(true);
    try {
      const values = await form.validateFields();
      const payload: SalesTargetUpdateInput = {
        targetAmount: values.targetAmount,
        reason: values.reason,
        remark: values.remark,
      };
      await updateSalesTarget(target.id, payload);
      message.success('销售目标已更新');
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
      open={Boolean(target)}
      title={target ? `编辑销售目标：${target.period ?? '-'}` : '编辑销售目标'}
      confirmLoading={saving}
      okText="保存"
      cancelText="取消"
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item
          name="targetAmount"
          label="目标金额"
          rules={[{ required: true, message: '请输入目标金额' }]}
        >
          <InputNumber min={0} precision={2} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="reason" label="调整原因" extra="金额变更时必填，将写入调整记录">
          <Input.TextArea rows={2} maxLength={200} showCount />
        </Form.Item>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={500} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** 销售目标 Tab */
function TargetTab() {
  const [form] = Form.useForm<PeriodFilterValues>();
  const [rows, setRows] = useState<SalesTargetView[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState<SalesTargetPageQuery>({});
  const [page, setPage] = useState({ current: 1, size: 10 });
  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<SalesTargetView | null>(null);
  const [detailTarget, setDetailTarget] = useState<SalesTargetView | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageSalesTargets({ ...filters, current: page.current, size: page.size });
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

  const handleSearch = (values: PeriodFilterValues) => {
    setFilters({
      period: values.period || undefined,
      periodType: values.periodType || undefined,
      scopeType: values.scopeType || undefined,
      ownerId: values.ownerId ?? null,
      deptId: values.deptId ?? null,
    });
    setPage((prev) => ({ ...prev, current: 1 }));
  };

  const handleReset = () => {
    form.resetFields();
    setFilters({});
    setPage((prev) => ({ ...prev, current: 1 }));
  };

  const handleDelete = async (row: SalesTargetView) => {
    try {
      await deleteSalesTarget(row.id);
      message.success('销售目标已删除');
      void loadData();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const columns: TableProps<SalesTargetView>['columns'] = [
    { title: '周期', dataIndex: 'period', width: 110, render: (value?: string | null) => value || '-' },
    {
      title: '类型',
      dataIndex: 'periodType',
      width: 80,
      render: (value?: string | null) => FORECAST_PERIOD_TYPE_LABEL[value ?? ''] ?? value ?? '-',
    },
    {
      title: '范围',
      dataIndex: 'scopeType',
      width: 160,
      render: (_: unknown, row) => scopeText(row.scopeType, row.ownerId, row.deptId),
    },
    {
      title: '目标金额',
      dataIndex: 'targetAmount',
      width: 160,
      align: 'right',
      render: (value?: number | string | null) => formatAmount(value),
    },
    { title: '备注', dataIndex: 'remark', ellipsis: true, render: (value?: string | null) => value || '-' },
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
      width: 230,
      render: (_, row) => (
        <Space size={0} wrap>
          <Button size="small" type="link" onClick={() => setDetailTarget(row)}>
            完成度
          </Button>
          {hasPermission('sys:crm:target:manage') && (
            <Button size="small" type="link" onClick={() => setEditTarget(row)}>
              编辑
            </Button>
          )}
          {hasPermission('sys:crm:target:delete') && (
            <Popconfirm
              title="删除销售目标"
              description="删除后不可恢复，确认继续？"
              okText="删除"
              cancelText="取消"
              onConfirm={() => void handleDelete(row)}
            >
              <Button size="small" type="link" danger>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <Space style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}>
        {hasPermission('sys:crm:target:manage') && (
          <Button type="primary" onClick={() => setCreateOpen(true)}>
            新建目标
          </Button>
        )}
      </Space>
      <Form form={form} layout="inline" onFinish={handleSearch} style={{ marginBottom: 16, rowGap: 12 }}>
        <Form.Item name="period">
          <Input placeholder="周期 yyyy-MM / yyyy-Qn" allowClear style={{ width: 200 }} />
        </Form.Item>
        <Form.Item name="periodType">
          <Select placeholder="周期类型" allowClear style={{ width: 120 }} options={FORECAST_PERIOD_TYPE_OPTIONS} />
        </Form.Item>
        <Form.Item name="scopeType">
          <Select placeholder="统计范围" allowClear style={{ width: 120 }} options={FORECAST_SCOPE_TYPE_OPTIONS} />
        </Form.Item>
        <Form.Item name="ownerId">
          <InputNumber min={1} precision={0} placeholder="负责人 ID" style={{ width: 130 }} />
        </Form.Item>
        <Form.Item name="deptId">
          <InputNumber min={1} precision={0} placeholder="部门 ID" style={{ width: 130 }} />
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
      <Table<SalesTargetView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 1100 }}
        pagination={{
          current: page.current,
          pageSize: page.size,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (current, size) => setPage({ current, size }),
        }}
      />

      <TargetCreateModal
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onSaved={() => {
          setCreateOpen(false);
          void loadData();
        }}
      />
      <TargetUpdateModal
        target={editTarget}
        onCancel={() => setEditTarget(null)}
        onSaved={() => {
          setEditTarget(null);
          void loadData();
        }}
      />
      <TargetDetailDrawer target={detailTarget} onClose={() => setDetailTarget(null)} />
    </>
  );
}

/** 销售预测与目标页 */
export default function ForecastPage() {
  const canForecast = hasPermission('sys:crm:forecast:list');
  const canTarget = hasPermission('sys:crm:target:list');

  if (!canForecast && !canTarget) {
    return (
      <Card title="销售预测与目标">
        <Alert type="warning" showIcon message="当前账号无销售预测或销售目标查看权限" />
      </Card>
    );
  }

  const items = [];
  if (canForecast) {
    items.push({ key: 'forecast', label: '销售预测', children: <ForecastTab /> });
  }
  if (canTarget) {
    items.push({ key: 'target', label: '销售目标', children: <TargetTab /> });
  }

  return (
    <Card title="销售预测与目标">
      <Tabs defaultActiveKey={items[0].key} items={items} />
    </Card>
  );
}
