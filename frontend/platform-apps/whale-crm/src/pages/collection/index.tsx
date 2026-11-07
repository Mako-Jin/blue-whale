// 回款计划：分期维护、逾期与催收优先级、承诺还款、催收记录
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Descriptions,
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
import type { FormListFieldData, TableProps } from 'antd';
import {
  addCollectionAction,
  getCollection,
  getContract,
  listCollectionActions,
  pageCollections,
  promiseCollection,
  saveCollectionPlan,
  type CollectionActionView,
  type CollectionPageQuery,
  type CollectionPlanView,
} from '../../api/commerce';
import { hasPermission } from '../../utils/permission';
import {
  COLLECTION_ACTION_LABEL,
  COLLECTION_ACTION_OPTIONS,
  COLLECTION_PRIORITY_COLOR,
  COLLECTION_PRIORITY_LABEL,
  COLLECTION_STATUS_LABEL,
  COLLECTION_STATUS_OPTIONS,
  formatDateTime,
} from '../../utils/dict';

/** 筛选条件 */
interface CollectionFilterValues {
  contractId?: number | null;
  customerId?: number | null;
  status?: string;
  overdueOnly?: boolean;
}

/** 分期表单值 */
interface InstallmentFormValues {
  plannedAmount: number;
  plannedDate: string;
  priorityOverride?: string;
  remark?: string;
}

/** 计划表单值 */
interface PlanFormValues {
  contractId: number;
  installments: InstallmentFormValues[];
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

interface PlanEditorProps {
  open: boolean;
  contractId?: number | null;
  onCancel: () => void;
  onSaved: () => void;
}

/** 回款分期编辑弹窗 */
function PlanEditorModal({ open, contractId, onCancel, onSaved }: PlanEditorProps) {
  const [form] = Form.useForm<PlanFormValues>();
  const [saving, setSaving] = useState(false);
  const [contractAmount, setContractAmount] = useState<number | null>(null);
  const [loadingAmount, setLoadingAmount] = useState(false);
  const installments = Form.useWatch('installments', form) as InstallmentFormValues[] | undefined;
  const total = (installments ?? []).reduce((sum, item) => sum + (Number(item?.plannedAmount) || 0), 0);

  useEffect(() => {
    if (!open) {
      return;
    }
    form.resetFields();
    setContractAmount(null);
    form.setFieldsValue({
      contractId: contractId ?? undefined,
      installments: [{ plannedAmount: 0, plannedDate: '' }],
    });
    if (contractId) {
      void loadContract(contractId);
    }
  }, [open, contractId, form]);

  const loadContract = async (id: number) => {
    setLoadingAmount(true);
    try {
      const contract = await getContract(id);
      setContractAmount(contract.amount === null || contract.amount === undefined ? null : Number(contract.amount));
    } catch (error) {
      setContractAmount(null);
      message.error(errorText(error));
    } finally {
      setLoadingAmount(false);
    }
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      const values = await form.validateFields();
      const payload = {
        contractId: values.contractId,
        installments: (values.installments ?? []).map((item) => ({
          plannedAmount: item.plannedAmount,
          plannedDate: item.plannedDate,
          priorityOverride: item.priorityOverride,
          remark: item.remark,
        })),
      };
      await saveCollectionPlan(payload);
      message.success('回款分期已保存');
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
      title="维护回款分期"
      width={900}
      okText="保存"
      cancelText="取消"
      confirmLoading={saving}
      onOk={() => void handleSave()}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item
          name="contractId"
          label="合同台账 ID"
          rules={[{ required: true, message: '请输入合同台账 ID' }]}
        >
          <InputNumber
            min={1}
            precision={0}
            style={{ width: 240 }}
            onBlur={() => {
              const id = form.getFieldValue('contractId');
              if (id) {
                void loadContract(Number(id));
              }
            }}
          />
        </Form.Item>
        <Space style={{ marginBottom: 12 }}>
          <Typography.Text>合同金额：</Typography.Text>
          <Typography.Text strong>
            {loadingAmount ? '读取中…' : formatAmount(contractAmount)}
          </Typography.Text>
          <Typography.Text>分期合计：</Typography.Text>
          <Typography.Text strong type={contractAmount !== null && Math.abs(total - contractAmount) > 0.01 ? 'danger' : 'success'}>
            {formatAmount(total)}
          </Typography.Text>
          {contractAmount !== null && Math.abs(total - contractAmount) > 0.01 && (
            <Typography.Text type="danger">分期合计必须等于合同金额</Typography.Text>
          )}
        </Space>
        <Form.List name="installments">
          {(fields, { add, remove }) => (
            <>
              <Table<FormListFieldData>
                rowKey="key"
                size="small"
                dataSource={fields}
                pagination={false}
                columns={[
                  {
                    title: '期次',
                    width: 70,
                    render: (_, field) => field.name + 1,
                  },
                  {
                    title: '计划金额',
                    width: 160,
                    render: (_, field) => (
                      <Form.Item
                        name={[field.name, 'plannedAmount']}
                        rules={[{ required: true, message: '请输入计划金额' }]}
                        style={{ marginBottom: 0 }}
                      >
                        <InputNumber min={0.01} precision={2} style={{ width: '100%' }} />
                      </Form.Item>
                    ),
                  },
                  {
                    title: '计划回款日',
                    width: 150,
                    render: (_, field) => (
                      <Form.Item
                        name={[field.name, 'plannedDate']}
                        rules={[{ required: true, message: '请输入计划回款日' }]}
                        style={{ marginBottom: 0 }}
                      >
                        <Input placeholder="yyyy-MM-dd" />
                      </Form.Item>
                    ),
                  },
                  {
                    title: '优先级',
                    width: 120,
                    render: (_, field) => (
                      <Form.Item name={[field.name, 'priorityOverride']} style={{ marginBottom: 0 }}>
                        <Select
                          allowClear
                          placeholder="按逾期自动"
                          options={[
                            { value: 'HIGH', label: '高' },
                            { value: 'MEDIUM', label: '中' },
                            { value: 'LOW', label: '低' },
                          ]}
                        />
                      </Form.Item>
                    ),
                  },
                  {
                    title: '备注',
                    width: 180,
                    render: (_, field) => (
                      <Form.Item name={[field.name, 'remark']} style={{ marginBottom: 0 }}>
                        <Input maxLength={500} />
                      </Form.Item>
                    ),
                  },
                  {
                    title: '操作',
                    width: 60,
                    render: (_, field) => (
                      <Button size="small" type="link" danger onClick={() => remove(field.name)}>
                        删除
                      </Button>
                    ),
                  },
                ]}
              />
              <Button
                type="dashed"
                block
                style={{ marginTop: 8 }}
                onClick={() => add({ plannedAmount: 0, plannedDate: '' })}
              >
                新增分期
              </Button>
            </>
          )}
        </Form.List>
      </Form>
    </Modal>
  );
}

interface PromiseModalProps {
  plan: CollectionPlanView | null;
  onCancel: () => void;
  onSaved: () => void;
}

/** 承诺还款弹窗 */
function PromiseModal({ plan, onCancel, onSaved }: PromiseModalProps) {
  const [form] = Form.useForm<{ promiseDate: string; promiseAmount: number; remark?: string }>();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (plan) {
      form.resetFields();
      form.setFieldsValue({
        promiseDate: plan.promiseDate ?? undefined,
        promiseAmount: plan.promiseAmount === null || plan.promiseAmount === undefined
          ? undefined
          : Number(plan.promiseAmount),
        remark: plan.remark ?? undefined,
      });
    }
  }, [plan, form]);

  const handleSave = async () => {
    if (!plan) {
      return;
    }
    setSaving(true);
    try {
      const values = await form.validateFields();
      await promiseCollection(plan.id, values);
      message.success('承诺还款已登记');
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
      open={Boolean(plan)}
      title="登记承诺还款"
      okText="保存"
      cancelText="取消"
      confirmLoading={saving}
      onOk={() => void handleSave()}
      onCancel={onCancel}
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="promiseDate"
          label="承诺还款日"
          rules={[{ required: true, message: '请输入承诺还款日' }]}
        >
          <Input placeholder="yyyy-MM-dd" />
        </Form.Item>
        <Form.Item
          name="promiseAmount"
          label="承诺金额"
          rules={[{ required: true, message: '请输入承诺金额' }]}
        >
          <InputNumber min={0.01} precision={2} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={3} maxLength={500} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
}

interface ActionModalProps {
  plan: CollectionPlanView | null;
  onCancel: () => void;
  onSaved: () => void;
}

/** 催收记录弹窗 */
function ActionModal({ plan, onCancel, onSaved }: ActionModalProps) {
  const [form] = Form.useForm<{
    actionType: string;
    content?: string;
    promiseDate?: string;
    promiseAmount?: number;
  }>();
  const [saving, setSaving] = useState(false);
  const actionType = Form.useWatch('actionType', form);

  useEffect(() => {
    if (plan) {
      form.resetFields();
      form.setFieldsValue({ actionType: 'CALL' });
    }
  }, [plan, form]);

  const handleSave = async () => {
    if (!plan) {
      return;
    }
    setSaving(true);
    try {
      const values = await form.validateFields();
      await addCollectionAction(plan.id, values);
      message.success('催收记录已登记');
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
      open={Boolean(plan)}
      title="新增催收记录"
      okText="保存"
      cancelText="取消"
      confirmLoading={saving}
      onOk={() => void handleSave()}
      onCancel={onCancel}
    >
      <Form form={form} layout="vertical">
        <Form.Item name="actionType" label="催收动作" rules={[{ required: true, message: '请选择催收动作' }]}>
          <Select options={COLLECTION_ACTION_OPTIONS} />
        </Form.Item>
        <Form.Item name="content" label="催收内容">
          <Input.TextArea rows={3} maxLength={1000} showCount />
        </Form.Item>
        {actionType === 'PROMISE' && (
          <>
            <Form.Item name="promiseDate" label="承诺还款日">
              <Input placeholder="yyyy-MM-dd" />
            </Form.Item>
            <Form.Item name="promiseAmount" label="承诺金额">
              <InputNumber min={0.01} precision={2} style={{ width: '100%' }} />
            </Form.Item>
          </>
        )}
      </Form>
    </Modal>
  );
}

/** 回款计划页 */
export default function CollectionPage() {
  const [filterForm] = Form.useForm<CollectionFilterValues>();
  const [rows, setRows] = useState<CollectionPlanView[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState<CollectionPageQuery>({});
  const [page, setPage] = useState({ current: 1, size: 10 });
  const [editorOpen, setEditorOpen] = useState(false);
  const [editorContractId, setEditorContractId] = useState<number | null>(null);
  const [detailPlan, setDetailPlan] = useState<CollectionPlanView | null>(null);
  const [detailActions, setDetailActions] = useState<CollectionActionView[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [promisePlan, setPromisePlan] = useState<CollectionPlanView | null>(null);
  const [actionPlan, setActionPlan] = useState<CollectionPlanView | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageCollections({ ...filters, current: page.current, size: page.size });
      setRows(result.records);
      setTotal(result.total);
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [filters, page]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const loadDetail = useCallback(async (row: CollectionPlanView) => {
    setDetailPlan(row);
    setDetailLoading(true);
    try {
      const [fresh, actions] = await Promise.all([getCollection(row.id), listCollectionActions(row.id)]);
      setDetailPlan(fresh);
      setDetailActions(actions);
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setDetailLoading(false);
    }
  }, []);

  const handleSearch = (values: CollectionFilterValues) => {
    setFilters({
      contractId: values.contractId ?? null,
      customerId: values.customerId ?? null,
      status: values.status || undefined,
      overdueOnly: values.overdueOnly,
    });
    setPage((prev) => ({ ...prev, current: 1 }));
  };

  const handleReset = () => {
    filterForm.resetFields();
    setFilters({});
    setPage((prev) => ({ ...prev, current: 1 }));
  };

  const columns: TableProps<CollectionPlanView>['columns'] = [
    { title: '合同 ID', dataIndex: 'contractId', width: 100, render: (value?: number | null) => value ?? '-' },
    { title: '客户 ID', dataIndex: 'customerId', width: 100, render: (value?: number | null) => value ?? '-' },
    { title: '期次', dataIndex: 'installmentNo', width: 70, render: (value?: number | null) => value ?? '-' },
    {
      title: '计划金额',
      dataIndex: 'plannedAmount',
      width: 140,
      align: 'right',
      render: (value?: number | string | null) => formatAmount(value),
    },
    { title: '计划回款日', dataIndex: 'plannedDate', width: 120, render: (value?: string | null) => value || '-' },
    {
      title: '逾期',
      dataIndex: 'overdueDays',
      width: 90,
      align: 'right',
      render: (value?: number | null) =>
        value && value > 0 ? <Tag color="red">{value} 天</Tag> : '-',
    },
    {
      title: '优先级',
      dataIndex: 'priority',
      width: 90,
      render: (value?: string | null) => (
        <Tag color={COLLECTION_PRIORITY_COLOR[value ?? '']}>
          {COLLECTION_PRIORITY_LABEL[value ?? ''] ?? value ?? '-'}
        </Tag>
      ),
    },
    {
      title: '承诺还款',
      dataIndex: 'promiseDate',
      width: 160,
      render: (value?: string | null, row?: CollectionPlanView) => (
        <Space size={4}>
          <span>{value || '-'}</span>
          {row?.promiseAmount !== null && row?.promiseAmount !== undefined && (
            <span>{formatAmount(row.promiseAmount)}</span>
          )}
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value?: string | null) => COLLECTION_STATUS_LABEL[value ?? ''] ?? value ?? '-',
    },
    { title: '负责人', dataIndex: 'ownerId', width: 90, render: (value?: number | null) => value ?? '-' },
    {
      title: '操作',
      key: 'action',
      fixed: 'right',
      width: 210,
      render: (_, row) => (
        <Space size={0} wrap>
          <Button size="small" type="link" onClick={() => void loadDetail(row)}>
            详情
          </Button>
          {hasPermission('sys:crm:collection:action') && (
            <Button size="small" type="link" onClick={() => setPromisePlan(row)}>
              承诺
            </Button>
          )}
          {hasPermission('sys:crm:collection:action') && (
            <Button size="small" type="link" onClick={() => setActionPlan(row)}>
              催收
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="回款计划"
      extra={
        hasPermission('sys:crm:collection:manage') ? (
          <Button
            type="primary"
            onClick={() => {
              setEditorContractId(null);
              setEditorOpen(true);
            }}
          >
            维护分期
          </Button>
        ) : null
      }
    >
      <Form form={filterForm} layout="inline" onFinish={handleSearch} style={{ marginBottom: 16, rowGap: 12 }}>
        <Form.Item name="contractId">
          <InputNumber min={1} precision={0} placeholder="合同 ID" style={{ width: 130 }} />
        </Form.Item>
        <Form.Item name="customerId">
          <InputNumber min={1} precision={0} placeholder="客户 ID" style={{ width: 130 }} />
        </Form.Item>
        <Form.Item name="status">
          <Select placeholder="计划状态" allowClear style={{ width: 130 }} options={COLLECTION_STATUS_OPTIONS} />
        </Form.Item>
        <Form.Item name="overdueOnly">
          <Select
            allowClear
            placeholder="是否逾期"
            style={{ width: 130 }}
            options={[
              { value: true, label: '仅看逾期' },
              { value: false, label: '全部' },
            ]}
          />
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
      <Table<CollectionPlanView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 1300 }}
        rowClassName={(row) => (row.overdue ? 'crm-collection-overdue' : '')}
        pagination={{
          current: page.current,
          pageSize: page.size,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (current, size) => setPage({ current, size }),
        }}
      />
      <Typography.Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
        分期合计必须等于合同金额；实收、税额与记账以 ERP 为准，CRM 只维护计划与催收记录。
      </Typography.Text>

      <PlanEditorModal
        open={editorOpen}
        contractId={editorContractId}
        onCancel={() => setEditorOpen(false)}
        onSaved={() => {
          setEditorOpen(false);
          void loadData();
        }}
      />
      <PromiseModal
        plan={promisePlan}
        onCancel={() => setPromisePlan(null)}
        onSaved={() => {
          setPromisePlan(null);
          void loadData();
        }}
      />
      <ActionModal
        plan={actionPlan}
        onCancel={() => setActionPlan(null)}
        onSaved={() => {
          setActionPlan(null);
          void loadData();
        }}
      />

      <Drawer
        open={Boolean(detailPlan)}
        title={detailPlan ? `回款计划详情 #${detailPlan.id}` : '回款计划详情'}
        width={760}
        onClose={() => setDetailPlan(null)}
      >
        {detailPlan && (
          <>
            <Descriptions bordered size="small" column={2} style={{ marginBottom: 16 }}>
              <Descriptions.Item label="合同 ID">{detailPlan.contractId}</Descriptions.Item>
              <Descriptions.Item label="客户 ID">{detailPlan.customerId}</Descriptions.Item>
              <Descriptions.Item label="期次">{detailPlan.installmentNo}</Descriptions.Item>
              <Descriptions.Item label="计划金额">{formatAmount(detailPlan.plannedAmount)}</Descriptions.Item>
              <Descriptions.Item label="计划回款日">{detailPlan.plannedDate || '-'}</Descriptions.Item>
              <Descriptions.Item label="状态">
                {COLLECTION_STATUS_LABEL[detailPlan.status] ?? detailPlan.status}
              </Descriptions.Item>
              <Descriptions.Item label="优先级">
                <Tag color={COLLECTION_PRIORITY_COLOR[detailPlan.priority]}>
                  {COLLECTION_PRIORITY_LABEL[detailPlan.priority] ?? detailPlan.priority}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="逾期">
                {detailPlan.overdueDays ? `${detailPlan.overdueDays} 天` : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="承诺还款日">{detailPlan.promiseDate || '-'}</Descriptions.Item>
              <Descriptions.Item label="承诺金额">{formatAmount(detailPlan.promiseAmount)}</Descriptions.Item>
              <Descriptions.Item label="备注" span={2}>
                {detailPlan.remark || '-'}
              </Descriptions.Item>
            </Descriptions>
            <Typography.Text strong>催收记录</Typography.Text>
            <Table<CollectionActionView>
              style={{ marginTop: 8 }}
              rowKey="id"
              size="small"
              loading={detailLoading}
              dataSource={detailActions}
              pagination={false}
              columns={[
                {
                  title: '动作',
                  dataIndex: 'actionType',
                  width: 110,
                  render: (value?: string | null) =>
                    COLLECTION_ACTION_LABEL[value ?? ''] ?? value ?? '-',
                },
                { title: '内容', dataIndex: 'content', render: (value?: string | null) => value || '-' },
                { title: '承诺还款日', dataIndex: 'promiseDate', width: 120, render: (value?: string | null) => value || '-' },
                {
                  title: '承诺金额',
                  dataIndex: 'promiseAmount',
                  width: 120,
                  align: 'right',
                  render: (value?: number | string | null) => formatAmount(value),
                },
                {
                  title: '操作人',
                  dataIndex: 'operatorId',
                  width: 90,
                  render: (value?: number | null) => value ?? '-',
                },
                {
                  title: '时间',
                  dataIndex: 'createTime',
                  width: 170,
                  render: (value?: string | null) => formatDateTime(value),
                },
              ]}
            />
          </>
        )}
      </Drawer>
    </Card>
  );
}
