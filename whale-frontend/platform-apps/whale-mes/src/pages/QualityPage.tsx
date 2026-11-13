// MES 车间质量页：检验计划 / 首件检验 / IPQC 巡检 / 不良品 / 处置 / 返工 / SPC / 质量看板
import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  DatePicker,
  Empty,
  Form,
  Input,
  InputNumber,
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
import type { TableProps, TabsProps } from 'antd';
import dayjs from 'dayjs';
import {
  closeMesNcr,
  completeMesRework,
  createMesDisposition,
  createMesInspectionTask,
  createMesNcr,
  executeMesDisposition,
  fetchMesFirstArticleGate,
  fetchMesQualityBoard,
  fetchMesSpcChart,
  getMesInspectionPlan,
  getMesInspectionTask,
  judgeMesInspectionTask,
  listMesWorkOrders,
  pageMesDispositions,
  pageMesInspectionPlans,
  pageMesInspectionTasks,
  pageMesNcrs,
  pageMesReworks,
  pageMesSpcRules,
  pageMesSpcSamples,
  saveMesInspectionPlan,
  saveMesSpcRule,
  startMesRework,
  submitMesInspectionResults,
  submitMesSpcSample,
  type MesDisposition,
  type MesDispositionInput,
  type MesFirstArticleGate,
  type MesInspectionPlan,
  type MesInspectionPlanInput,
  type MesInspectionPlanItem,
  type MesInspectionResultInput,
  type MesInspectionTask,
  type MesInspectionTaskInput,
  type MesInspectionType,
  type MesNcr,
  type MesNcrInput,
  type MesQualityBoard,
  type MesReworkOrder,
  type MesSpcChartPoint,
  type MesSpcRule,
  type MesSpcRuleInput,
  type MesSpcSample,
  type MesWorkOrder,
} from '../api/mes';
import { hasPermission } from '../utils/permission';

/** 页签键 */
export type QualityTab =
  'plan' | 'first-article' | 'ipqc' | 'ncr' | 'disposition' | 'rework' | 'spc' | 'board';

/** 检验类型文案 */
const INSPECTION_TYPE_LABEL: Record<string, string> = {
  FIRST_ARTICLE: '首件检验',
  IPQC: 'IPQC 巡检',
};

/** 检验方式文案 */
const INSPECTION_METHOD_LABEL: Record<string, string> = {
  VISUAL: '目视',
  MEASURE: '测量',
  FUNCTION: '功能',
  TEST: '试装',
};

/** 检验任务状态文案 */
const TASK_STATUS_LABEL: Record<string, string> = {
  PENDING: '待检',
  INSPECTED: '已录入',
  PASSED: '合格',
  FAILED: '不合格',
  CANCELLED: '已取消',
};

/** 检验任务状态颜色 */
const TASK_STATUS_COLOR: Record<string, string> = {
  PENDING: 'blue',
  INSPECTED: 'gold',
  PASSED: 'green',
  FAILED: 'red',
  CANCELLED: 'default',
};

/** NCR 状态文案 */
const NCR_STATUS_LABEL: Record<string, string> = {
  OPEN: '待处置',
  DISPOSITION: '处置中',
  REWORK: '返工中',
  REPAIR: '返修中',
  SCRAP: '已报废',
  CONCESSION: '让步接收',
  CLOSED: '已关闭',
};

/** NCR 状态颜色 */
const NCR_STATUS_COLOR: Record<string, string> = {
  OPEN: 'red',
  DISPOSITION: 'orange',
  REWORK: 'blue',
  REPAIR: 'cyan',
  SCRAP: 'default',
  CONCESSION: 'purple',
  CLOSED: 'green',
};

/** 可关闭的 NCR 状态 */
const NCR_CLOSABLE_STATUS = ['REWORK', 'REPAIR', 'SCRAP', 'CONCESSION'];

/** 处置类型文案 */
const DISPOSITION_TYPE_LABEL: Record<string, string> = {
  REWORK: '返工',
  REPAIR: '返修',
  SCRAP: '报废',
  CONCESSION: '让步接收',
};

/** 处置状态文案 */
const DISPOSITION_STATUS_LABEL: Record<string, string> = {
  CONFIRMED: '已确认',
  EXECUTED: '已执行',
  CANCELLED: '已取消',
};

/** 返工状态文案 */
const REWORK_STATUS_LABEL: Record<string, string> = {
  CREATED: '待开工',
  IN_PROGRESS: '返工中',
  COMPLETED: '已完工',
  CANCELLED: '已取消',
};

/** 返工状态颜色 */
const REWORK_STATUS_COLOR: Record<string, string> = {
  CREATED: 'blue',
  IN_PROGRESS: 'processing',
  COMPLETED: 'green',
  CANCELLED: 'default',
};

/** 检验任务状态下拉项 */
const TASK_STATUS_OPTIONS = Object.entries(TASK_STATUS_LABEL).map(([value, label]) => ({
  value,
  label,
}));

/** NCR 状态下拉项 */
const NCR_STATUS_OPTIONS = Object.entries(NCR_STATUS_LABEL).map(([value, label]) => ({
  value,
  label,
}));

/** 处置类型下拉项 */
const DISPOSITION_TYPE_OPTIONS = Object.entries(DISPOSITION_TYPE_LABEL).map(([value, label]) => ({
  value,
  label,
}));

/** 数量展示 */
function formatQuantity(value?: number | string): string {
  return value === undefined || value === null || value === '' ? '-' : String(value);
}

/** 时间展示 */
function formatTime(value?: string): string {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-';
}

/** 字符串数值转数字（空值 / 非法值返回 undefined） */
function toNumber(value?: number | string): number | undefined {
  if (value === undefined || value === null || value === '') {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isNaN(parsed) ? undefined : parsed;
}

/** 均值 */
function average(values: number[]): number {
  return values.length === 0 ? 0 : values.reduce((sum, value) => sum + value, 0) / values.length;
}

/** 检验计划维护面板 */
function PlanPanel() {
  const [plans, setPlans] = useState<MesInspectionPlan[]>([]);
  const [loading, setLoading] = useState(false);
  const [planNo, setPlanNo] = useState<string>();
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<MesInspectionPlan | null>(null);
  const [form] = Form.useForm<MesInspectionPlanInput>();
  const canEdit = hasPermission('sys:mes:quality:plan');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setPlans((await pageMesInspectionPlans({ planNo })).records);
    } finally {
      setLoading(false);
    }
  }, [planNo]);

  useEffect(() => {
    void load();
  }, [load]);

  const openForm = async (plan: MesInspectionPlan | null) => {
    setEditing(plan);
    form.resetFields();
    if (plan) {
      const detail = await getMesInspectionPlan(plan.id);
      form.setFieldsValue({
        planNo: detail.planNo,
        planName: detail.planName,
        inspectionType: detail.inspectionType,
        itemCode: detail.itemCode,
        operationCode: detail.operationCode,
        lineCode: detail.lineCode,
        sampleSize: detail.sampleSize,
        frequencyMinutes: detail.frequencyMinutes,
        autoNcr: detail.autoNcr,
        status: detail.status,
        remark: detail.remark,
        items: detail.items ?? [],
      });
    } else {
      form.setFieldsValue({
        inspectionType: 'FIRST_ARTICLE',
        sampleSize: 1,
        frequencyMinutes: 0,
        autoNcr: 1,
        status: 'ENABLED',
        items: [
          { itemNo: 1, itemName: '', inspectionMethod: 'VISUAL', criticalFlag: 0, sortNo: 1 },
        ],
      });
    }
    setOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) {
      return;
    }
    try {
      await saveMesInspectionPlan({
        ...values,
        id: editing?.id,
        items: (values.items ?? []).map((item, index) => ({ ...item, sortNo: index })),
      });
      message.success('检验计划已保存');
      setOpen(false);
      await load();
    } catch {
      message.error('保存失败：计划编号需唯一，且已产生检验任务的计划不可修改');
    }
  };

  const columns: TableProps<MesInspectionPlan>['columns'] = [
    { title: '计划编号', dataIndex: 'planNo', key: 'planNo', width: 170 },
    { title: '计划名称', dataIndex: 'planName', key: 'planName', width: 160 },
    {
      title: '检验类型',
      dataIndex: 'inspectionType',
      key: 'inspectionType',
      width: 110,
      render: (value: string) => <Tag color="blue">{INSPECTION_TYPE_LABEL[value] ?? value}</Tag>,
    },
    { title: '物料编码', dataIndex: 'itemCode', key: 'itemCode', width: 130 },
    { title: '工序编码', dataIndex: 'operationCode', key: 'operationCode', width: 120 },
    { title: '抽样数', dataIndex: 'sampleSize', key: 'sampleSize', width: 90 },
    { title: '巡检频次(分)', dataIndex: 'frequencyMinutes', key: 'frequencyMinutes', width: 120 },
    {
      title: '自动 NCR',
      dataIndex: 'autoNcr',
      key: 'autoNcr',
      width: 100,
      render: (value: number) => (value === 1 ? '是' : '否'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (value: string) => (
        <Tag color={value === 'ENABLED' ? 'green' : 'default'}>
          {value === 'ENABLED' ? '启用' : '停用'}
        </Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 90,
      render: (_, record) =>
        canEdit ? (
          <Button type="link" size="small" onClick={() => void openForm(record)}>
            编辑
          </Button>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="计划编号"
          style={{ width: 220 }}
          onSearch={(value) => setPlanNo(value || undefined)}
        />
        {canEdit ? (
          <Button type="primary" onClick={() => void openForm(null)}>
            新增检验计划
          </Button>
        ) : null}
      </Space>
      <Table<MesInspectionPlan>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={plans}
        scroll={{ x: 1100 }}
      />
      <Modal
        open={open}
        title={editing ? '编辑检验计划' : '新增检验计划'}
        width={1100}
        onOk={submit}
        onCancel={() => setOpen(false)}
        okText="保存"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="planNo" label="计划编号（空则自动生成）">
                <Input maxLength={64} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="planName"
                label="计划名称"
                rules={[{ required: true, message: '请填写计划名称' }]}
              >
                <Input maxLength={128} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="inspectionType"
                label="检验类型"
                rules={[{ required: true, message: '请选择检验类型' }]}
              >
                <Select
                  options={Object.entries(INSPECTION_TYPE_LABEL).map(([value, label]) => ({
                    value,
                    label,
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="itemCode" label="适用物料">
                <Input maxLength={64} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="operationCode" label="适用工序">
                <Input maxLength={64} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="lineCode" label="适用产线">
                <Input maxLength={64} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="sampleSize"
                label="抽样数量"
                rules={[{ required: true, message: '请填写抽样数量' }]}
              >
                <InputNumber min={1} max={200} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="frequencyMinutes" label="巡检频次（分钟）">
                <InputNumber min={0} max={1440} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="autoNcr" label="不合格自动生成 NCR">
                <Select
                  options={[
                    { value: 1, label: '是' },
                    { value: 0, label: '否' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="status" label="状态">
                <Select
                  options={[
                    { value: 'ENABLED', label: '启用' },
                    { value: 'DISABLED', label: '停用' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={16}>
              <Form.Item name="remark" label="备注">
                <Input maxLength={255} />
              </Form.Item>
            </Col>
          </Row>
          <Typography.Title level={5}>检验项</Typography.Title>
          <Form.List
            name="items"
            rules={[
              {
                validator: async (_, items: MesInspectionPlanItem[]) => {
                  if (!items || items.length === 0) {
                    return Promise.reject(new Error('至少配置一个检验项'));
                  }
                  const seen = new Set<number>();
                  for (const item of items) {
                    if (seen.has(item.itemNo)) {
                      return Promise.reject(new Error(`检验项序号重复：${item.itemNo}`));
                    }
                    seen.add(item.itemNo);
                  }
                  return Promise.resolve();
                },
              },
            ]}
          >
            {(fields, { add, remove }, { errors }) => (
              <Space direction="vertical" size="small" style={{ width: '100%' }}>
                {fields.map((field) => (
                  <Space key={field.key} wrap align="baseline">
                    <Form.Item
                      {...field}
                      name={[field.name, 'itemNo']}
                      rules={[{ required: true, message: '序号' }]}
                    >
                      <InputNumber min={1} placeholder="序号" style={{ width: 80 }} />
                    </Form.Item>
                    <Form.Item
                      {...field}
                      name={[field.name, 'itemName']}
                      rules={[{ required: true, message: '名称' }]}
                    >
                      <Input placeholder="检验项名称" maxLength={128} style={{ width: 180 }} />
                    </Form.Item>
                    <Form.Item {...field} name={[field.name, 'inspectionMethod']}>
                      <Select
                        style={{ width: 110 }}
                        options={Object.entries(INSPECTION_METHOD_LABEL).map(([value, label]) => ({
                          value,
                          label,
                        }))}
                      />
                    </Form.Item>
                    <Form.Item {...field} name={[field.name, 'standardText']}>
                      <Input placeholder="标准描述" maxLength={128} style={{ width: 160 }} />
                    </Form.Item>
                    <Form.Item {...field} name={[field.name, 'minValue']}>
                      <InputNumber placeholder="下限" style={{ width: 100 }} />
                    </Form.Item>
                    <Form.Item {...field} name={[field.name, 'maxValue']}>
                      <InputNumber placeholder="上限" style={{ width: 100 }} />
                    </Form.Item>
                    <Form.Item {...field} name={[field.name, 'unit']}>
                      <Input placeholder="单位" maxLength={16} style={{ width: 80 }} />
                    </Form.Item>
                    <Form.Item {...field} name={[field.name, 'criticalFlag']}>
                      <Select
                        style={{ width: 100 }}
                        options={[
                          { value: 1, label: '关键项' },
                          { value: 0, label: '一般项' },
                        ]}
                      />
                    </Form.Item>
                    <Button type="link" danger onClick={() => remove(field.name)}>
                      删除
                    </Button>
                  </Space>
                ))}
                <Button
                  type="dashed"
                  block
                  onClick={() =>
                    add({ itemNo: fields.length + 1, inspectionMethod: 'VISUAL', criticalFlag: 0 })
                  }
                >
                  添加检验项
                </Button>
                <Form.ErrorList errors={errors} />
              </Space>
            )}
          </Form.List>
        </Form>
      </Modal>
    </Space>
  );
}

/** 首件门禁查询卡片 */
function FirstArticleGateCard() {
  const [workOrders, setWorkOrders] = useState<MesWorkOrder[]>([]);
  const [gate, setGate] = useState<MesFirstArticleGate | null>(null);
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm<{ workOrderId: string; operationCode?: string }>();

  useEffect(() => {
    void (async () => {
      setWorkOrders(await listMesWorkOrders());
    })();
  }, []);

  const query = async (values: { workOrderId: string; operationCode?: string }) => {
    setLoading(true);
    try {
      setGate(await fetchMesFirstArticleGate(values));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card title="首件门禁校验" size="small" loading={loading}>
      <Form form={form} layout="inline" onFinish={(values) => void query(values)}>
        <Form.Item
          name="workOrderId"
          label="工单"
          rules={[{ required: true, message: '请选择工单' }]}
        >
          <Select
            showSearch
            optionFilterProp="label"
            style={{ width: 260 }}
            options={workOrders.map((order) => ({
              value: order.id,
              label: `${order.code} ${order.name}`.trim(),
            }))}
          />
        </Form.Item>
        <Form.Item name="operationCode" label="工序编码">
          <Input allowClear placeholder="不填则取该工单全部工序" style={{ width: 200 }} />
        </Form.Item>
        <Button type="primary" htmlType="submit">
          查询门禁
        </Button>
      </Form>
      {gate ? (
        <Alert
          style={{ marginTop: 16 }}
          type={gate.passed ? 'success' : 'error'}
          showIcon
          message={gate.message}
          description={
            <Space wrap size="middle">
              <span>最近任务：{gate.taskNo ?? '-'}</span>
              <span>
                状态：{gate.status ? (TASK_STATUS_LABEL[gate.status] ?? gate.status) : '-'}
              </span>
              <span>判定时间：{formatTime(gate.judgedAt)}</span>
            </Space>
          }
        />
      ) : null}
    </Card>
  );
}

/** 检验结果表单值 */
interface InspectionResultFormValues {
  inspector?: string;
  remark?: string;
  results: MesInspectionResultInput[];
}

/** 首件 / IPQC 检验面板 */
function InspectionPanel({
  taskType,
  withGate = false,
}: {
  taskType: MesInspectionType;
  withGate?: boolean;
}) {
  const [tasks, setTasks] = useState<MesInspectionTask[]>([]);
  const [plans, setPlans] = useState<MesInspectionPlan[]>([]);
  const [loading, setLoading] = useState(false);
  const [taskNo, setTaskNo] = useState<string>();
  const [status, setStatus] = useState<string>();
  const [createOpen, setCreateOpen] = useState(false);
  const [resultOpen, setResultOpen] = useState(false);
  const [judgeOpen, setJudgeOpen] = useState(false);
  const [currentTask, setCurrentTask] = useState<MesInspectionTask | null>(null);
  const [planItems, setPlanItems] = useState<MesInspectionPlanItem[]>([]);
  const [createForm] = Form.useForm<MesInspectionTaskInput>();
  const [resultForm] = Form.useForm<InspectionResultFormValues>();
  const [judgeForm] = Form.useForm<{ judge?: string; conclusion: string }>();
  const canInspect = hasPermission('sys:mes:quality:inspection');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setTasks((await pageMesInspectionTasks({ taskType, taskNo, status })).records);
    } finally {
      setLoading(false);
    }
  }, [taskType, taskNo, status]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    void (async () => {
      setPlans(
        (await pageMesInspectionPlans({ inspectionType: taskType, status: 'ENABLED' })).records,
      );
    })();
  }, [taskType]);

  const openCreate = () => {
    createForm.resetFields();
    createForm.setFieldsValue({ taskType, sampleSize: 1 });
    setCreateOpen(true);
  };

  const submitCreate = async () => {
    const values = await createForm.validateFields().catch(() => null);
    if (!values) {
      return;
    }
    try {
      await createMesInspectionTask({ ...values, taskType });
      message.success('检验任务已创建');
      setCreateOpen(false);
      await load();
    } catch {
      message.error('创建失败：请确认检验计划已启用且已配置检验项');
    }
  };

  const openResults = async (task: MesInspectionTask) => {
    setCurrentTask(task);
    const detail = await getMesInspectionTask(task.id);
    const items = task.planId ? ((await getMesInspectionPlan(task.planId)).items ?? []) : [];
    const existing = new Map(
      detail.results.map((result) => [String(result.planItemId ?? ''), result]),
    );
    setPlanItems(items);
    resultForm.resetFields();
    resultForm.setFieldsValue({
      inspector: task.inspector,
      remark: task.remark,
      results: items.map((item) => {
        const found = existing.get(String(item.id ?? ''));
        return {
          planItemId: String(item.id ?? ''),
          itemNo: item.itemNo,
          itemName: item.itemName,
          sampleNo: found?.sampleNo ?? 1,
          measuredValue: found?.measuredValue,
          numericValue: toNumber(found?.numericValue),
          result: found?.result ?? 'OK',
          remark: found?.remark,
        };
      }),
    });
    setResultOpen(true);
  };

  const submitResults = async () => {
    if (!currentTask) {
      return;
    }
    const values = await resultForm.validateFields().catch(() => null);
    if (!values) {
      return;
    }
    try {
      await submitMesInspectionResults(currentTask.id, values);
      message.success('检验结果已录入');
      setResultOpen(false);
      await load();
    } catch {
      message.error('录入失败：测量项合格结果不得越出上下限');
    }
  };

  const openJudge = (task: MesInspectionTask) => {
    setCurrentTask(task);
    judgeForm.resetFields();
    judgeForm.setFieldsValue({ conclusion: '检验项全部完成，结论待确认' });
    setJudgeOpen(true);
  };

  const submitJudge = async () => {
    if (!currentTask) {
      return;
    }
    const values = await judgeForm.validateFields().catch(() => null);
    if (!values) {
      return;
    }
    try {
      const judged = await judgeMesInspectionTask(currentTask.id, values);
      message.success(judged.status === 'FAILED' ? '判定不合格，已自动生成 NCR' : '判定合格');
      setJudgeOpen(false);
      await load();
    } catch {
      message.error('判定失败：检验项未全部录入或任务状态不可判定');
    }
  };

  const columns: TableProps<MesInspectionTask>['columns'] = [
    { title: '任务号', dataIndex: 'taskNo', key: 'taskNo', width: 170 },
    { title: '计划编号', dataIndex: 'planNo', key: 'planNo', width: 150 },
    { title: '工单编码', dataIndex: 'workOrderCode', key: 'workOrderCode', width: 140 },
    { title: '工序', dataIndex: 'operationCode', key: 'operationCode', width: 110 },
    { title: '批次号', dataIndex: 'batchNo', key: 'batchNo', width: 150 },
    { title: '应检数', dataIndex: 'sampleSize', key: 'sampleSize', width: 90 },
    { title: '已检项', dataIndex: 'inspectedQuantity', key: 'inspectedQuantity', width: 90 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: string) => (
        <Tag color={TASK_STATUS_COLOR[value] ?? 'default'}>{TASK_STATUS_LABEL[value] ?? value}</Tag>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 160,
      render: (value?: string) => formatTime(value),
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_, record) => (
        <Space size="small">
          {canInspect && (record.status === 'PENDING' || record.status === 'INSPECTED') ? (
            <Button type="link" size="small" onClick={() => void openResults(record)}>
              录入
            </Button>
          ) : null}
          {canInspect && ['PENDING', 'INSPECTED'].includes(record.status) ? (
            <Button type="link" size="small" onClick={() => openJudge(record)}>
              判定
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      {withGate ? <FirstArticleGateCard /> : null}
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="检验任务号"
          style={{ width: 220 }}
          onSearch={(value) => setTaskNo(value || undefined)}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 140 }}
          value={status}
          onChange={(value) => setStatus(value)}
          options={TASK_STATUS_OPTIONS}
        />
        {canInspect ? (
          <Button type="primary" onClick={openCreate}>
            新建{INSPECTION_TYPE_LABEL[taskType]}任务
          </Button>
        ) : null}
      </Space>
      <Table<MesInspectionTask>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={tasks}
        scroll={{ x: 1200 }}
      />
      <Modal
        open={createOpen}
        title={`新建${INSPECTION_TYPE_LABEL[taskType]}任务`}
        onOk={submitCreate}
        onCancel={() => setCreateOpen(false)}
        okText="创建"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={createForm} layout="vertical">
          <Form.Item
            name="planId"
            label="检验计划"
            rules={[{ required: true, message: '请选择检验计划' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              options={plans.map((plan) => ({
                value: plan.id,
                label: `${plan.planNo} ${plan.planName}`,
              }))}
            />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="workOrderCode" label="工单编码">
                <Input maxLength={64} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="operationCode" label="工序编码（空则取计划工序）">
                <Input maxLength={64} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="lineCode" label="产线编码">
                <Input maxLength={64} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="stationCode" label="工位编码">
                <Input maxLength={64} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="batchNo" label="批次号">
                <Input maxLength={64} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="sampleSize" label="应检样本数（空则取计划）">
                <InputNumber min={1} max={200} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="inspector" label="检验员">
                <Input maxLength={64} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="remark" label="备注">
                <Input maxLength={255} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
      <Modal
        open={resultOpen}
        title={`录入检验结果 ${currentTask?.taskNo ?? ''}`}
        width={900}
        onOk={submitResults}
        onCancel={() => setResultOpen(false)}
        okText="提交"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={resultForm} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="inspector" label="检验员">
                <Input maxLength={64} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="remark" label="备注">
                <Input maxLength={255} />
              </Form.Item>
            </Col>
          </Row>
          {planItems.length === 0 ? <Empty description="检验计划未配置检验项" /> : null}
          {planItems.map((item, index) => (
            <Card key={item.id ?? index} size="small" style={{ marginBottom: 8 }}>
              <Space direction="vertical" size="small" style={{ width: '100%' }}>
                <Space wrap>
                  <Typography.Text strong>
                    {item.itemNo}. {item.itemName}
                  </Typography.Text>
                  <Tag>
                    {INSPECTION_METHOD_LABEL[item.inspectionMethod ?? ''] ??
                      item.inspectionMethod ??
                      '-'}
                  </Tag>
                  {item.standardText ? (
                    <Typography.Text type="secondary">{item.standardText}</Typography.Text>
                  ) : null}
                  {item.minValue !== undefined || item.maxValue !== undefined ? (
                    <Typography.Text type="secondary">
                      范围 {formatQuantity(item.minValue)} ~ {formatQuantity(item.maxValue)}{' '}
                      {item.unit ?? ''}
                    </Typography.Text>
                  ) : null}
                  {item.criticalFlag === 1 ? <Tag color="red">关键项</Tag> : null}
                </Space>
                <Space wrap align="baseline">
                  <Form.Item name={['results', index, 'planItemId']} hidden>
                    <Input />
                  </Form.Item>
                  <Form.Item name={['results', index, 'itemNo']} hidden>
                    <InputNumber />
                  </Form.Item>
                  <Form.Item name={['results', index, 'itemName']} hidden>
                    <Input />
                  </Form.Item>
                  <Form.Item
                    name={['results', index, 'result']}
                    rules={[{ required: true, message: '请选择判定' }]}
                  >
                    <Select
                      style={{ width: 100 }}
                      options={[
                        { value: 'OK', label: '合格' },
                        { value: 'NG', label: '不合格' },
                      ]}
                    />
                  </Form.Item>
                  <Form.Item name={['results', index, 'measuredValue']}>
                    <Input placeholder="实测值文本" maxLength={128} style={{ width: 180 }} />
                  </Form.Item>
                  <Form.Item name={['results', index, 'numericValue']}>
                    <InputNumber placeholder="实测数值" style={{ width: 140 }} />
                  </Form.Item>
                  <Form.Item name={['results', index, 'sampleNo']}>
                    <InputNumber min={1} placeholder="样本序号" style={{ width: 110 }} />
                  </Form.Item>
                  <Form.Item name={['results', index, 'remark']}>
                    <Input placeholder="备注" maxLength={255} style={{ width: 200 }} />
                  </Form.Item>
                </Space>
              </Space>
            </Card>
          ))}
        </Form>
      </Modal>
      <Modal
        open={judgeOpen}
        title={`检验判定 ${currentTask?.taskNo ?? ''}`}
        onOk={submitJudge}
        onCancel={() => setJudgeOpen(false)}
        okText="判定"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={judgeForm} layout="vertical">
          <Form.Item
            name="conclusion"
            label="判定结论"
            rules={[{ required: true, message: '请填写判定结论' }]}
          >
            <Input.TextArea rows={3} maxLength={255} />
          </Form.Item>
          <Form.Item name="judge" label="判定人">
            <Input maxLength={64} />
          </Form.Item>
          <Alert
            type="info"
            showIcon
            message="检验项未全部录入不可判定；判定不合格将自动生成 NCR。"
          />
        </Form>
      </Modal>
    </Space>
  );
}

/** 不良品 NCR 面板 */
function NcrPanel() {
  const [records, setRecords] = useState<MesNcr[]>([]);
  const [loading, setLoading] = useState(false);
  const [ncrNo, setNcrNo] = useState<string>();
  const [status, setStatus] = useState<string>();
  const [createOpen, setCreateOpen] = useState(false);
  const [dispositionOpen, setDispositionOpen] = useState(false);
  const [closeOpen, setCloseOpen] = useState(false);
  const [activeNcr, setActiveNcr] = useState<MesNcr | null>(null);
  const [createForm] = Form.useForm<MesNcrInput>();
  const [dispositionForm] = Form.useForm<MesDispositionInput>();
  const [closeForm] = Form.useForm<{ conclusion: string; closedBy?: string }>();
  const canNcr = hasPermission('sys:mes:quality:ncr');
  const canDisposition = hasPermission('sys:mes:quality:disposition');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRecords((await pageMesNcrs({ ncrNo, status })).records);
    } finally {
      setLoading(false);
    }
  }, [ncrNo, status]);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    createForm.resetFields();
    createForm.setFieldsValue({ defectQuantity: 1 });
    setCreateOpen(true);
  };

  const submitCreate = async () => {
    const values = await createForm.validateFields().catch(() => null);
    if (!values) {
      return;
    }
    try {
      await createMesNcr(values);
      message.success('NCR 已创建');
      setCreateOpen(false);
      await load();
    } catch {
      message.error('创建失败：不良数量必须大于 0');
    }
  };

  const openDisposition = (ncr: MesNcr) => {
    setActiveNcr(ncr);
    dispositionForm.resetFields();
    dispositionForm.setFieldsValue({
      ncrId: ncr.id,
      dispositionType: 'REWORK',
      quantity: toNumber(ncr.defectQuantity) ?? 0,
      approvedBy: undefined,
      remark: undefined,
    });
    setDispositionOpen(true);
  };

  const submitDisposition = async () => {
    if (!activeNcr) {
      return;
    }
    const values = await dispositionForm.validateFields().catch(() => null);
    if (!values) {
      return;
    }
    try {
      await createMesDisposition({ ...values, ncrId: activeNcr.id });
      message.success('处置已创建');
      setDispositionOpen(false);
      await load();
    } catch {
      message.error('处置失败：数量必须等于不良数量，且一个 NCR 仅允许一条处置');
    }
  };

  const openClose = (ncr: MesNcr) => {
    setActiveNcr(ncr);
    closeForm.resetFields();
    setCloseOpen(true);
  };

  const submitClose = async () => {
    if (!activeNcr) {
      return;
    }
    const values = await closeForm.validateFields().catch(() => null);
    if (!values) {
      return;
    }
    try {
      await closeMesNcr(activeNcr.id, values);
      message.success('NCR 已关闭');
      setCloseOpen(false);
      await load();
    } catch {
      message.error('关闭失败：需完成处置执行，返工未完成不可关闭');
    }
  };

  const columns: TableProps<MesNcr>['columns'] = [
    { title: 'NCR 编号', dataIndex: 'ncrNo', key: 'ncrNo', width: 170 },
    { title: '来源', dataIndex: 'sourceType', key: 'sourceType', width: 100 },
    { title: '检验任务', dataIndex: 'inspectionTaskNo', key: 'inspectionTaskNo', width: 160 },
    { title: '工单编码', dataIndex: 'workOrderCode', key: 'workOrderCode', width: 140 },
    { title: '物料', dataIndex: 'itemName', key: 'itemName', width: 130 },
    { title: '批次号', dataIndex: 'batchNo', key: 'batchNo', width: 150 },
    {
      title: '不良',
      key: 'defect',
      width: 160,
      render: (_, record) =>
        `${record.defectName ?? record.defectCode ?? '-'} × ${formatQuantity(record.defectQuantity)}`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: string) => (
        <Tag color={NCR_STATUS_COLOR[value] ?? 'default'}>{NCR_STATUS_LABEL[value] ?? value}</Tag>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 160,
      render: (value?: string) => formatTime(value),
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_, record) => (
        <Space size="small">
          {canDisposition && record.status === 'OPEN' ? (
            <Button type="link" size="small" onClick={() => openDisposition(record)}>
              处置
            </Button>
          ) : null}
          {canNcr && NCR_CLOSABLE_STATUS.includes(record.status) ? (
            <Button type="link" size="small" onClick={() => openClose(record)}>
              关闭
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="NCR 编号"
          style={{ width: 220 }}
          onSearch={(value) => setNcrNo(value || undefined)}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 140 }}
          value={status}
          onChange={(value) => setStatus(value)}
          options={NCR_STATUS_OPTIONS}
        />
        {canNcr ? (
          <Button type="primary" onClick={openCreate}>
            新建 NCR
          </Button>
        ) : null}
      </Space>
      <Table<MesNcr>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={records}
        scroll={{ x: 1400 }}
      />
      <Modal
        open={createOpen}
        title="新建不合格品记录"
        onOk={submitCreate}
        onCancel={() => setCreateOpen(false)}
        okText="创建"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={createForm} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="workOrderCode" label="工单编码">
                <Input maxLength={64} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="operationCode" label="工序编码">
                <Input maxLength={64} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="itemCode" label="物料编码">
                <Input maxLength={64} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="itemName" label="物料名称">
                <Input maxLength={128} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="batchNo" label="批次号">
                <Input maxLength={64} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="defectQuantity"
                label="不良数量"
                rules={[{ required: true, message: '请填写不良数量' }]}
              >
                <InputNumber min={0.0001} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="defectCode" label="不良代码">
                <Input maxLength={64} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="defectName" label="不良名称">
                <Input maxLength={128} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={dispositionOpen}
        title={`NCR 处置 ${activeNcr?.ncrNo ?? ''}`}
        onOk={submitDisposition}
        onCancel={() => setDispositionOpen(false)}
        okText="提交处置"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={dispositionForm} layout="vertical">
          <Form.Item name="ncrId" hidden>
            <Input />
          </Form.Item>
          <Form.Item
            name="dispositionType"
            label="处置类型"
            rules={[{ required: true, message: '请选择处置类型' }]}
          >
            <Select options={DISPOSITION_TYPE_OPTIONS} />
          </Form.Item>
          <Form.Item label="处置数量（须等于不良数量）">
            <Form.Item
              name="quantity"
              noStyle
              rules={[{ required: true, message: '请填写处置数量' }]}
            >
              <InputNumber min={0.0001} style={{ width: '100%' }} />
            </Form.Item>
          </Form.Item>
          <Form.Item name="reason" label="处置原因">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
          <Form.Item name="approvedBy" label="审批人">
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={closeOpen}
        title={`关闭 NCR ${activeNcr?.ncrNo ?? ''}`}
        onOk={submitClose}
        onCancel={() => setCloseOpen(false)}
        okText="关闭"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={closeForm} layout="vertical">
          <Form.Item
            name="conclusion"
            label="关闭结论"
            rules={[{ required: true, message: '关闭 NCR 必须填写结论' }]}
          >
            <Input.TextArea rows={3} maxLength={255} />
          </Form.Item>
          <Form.Item name="closedBy" label="关闭人">
            <Input maxLength={64} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

/** 处置面板 */
function DispositionPanel() {
  const [records, setRecords] = useState<MesDisposition[]>([]);
  const [loading, setLoading] = useState(false);
  const [dispositionNo, setDispositionNo] = useState<string>();
  const [status, setStatus] = useState<string>();
  const canDisposition = hasPermission('sys:mes:quality:disposition');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRecords((await pageMesDispositions({ dispositionNo, status })).records);
    } finally {
      setLoading(false);
    }
  }, [dispositionNo, status]);

  useEffect(() => {
    void load();
  }, [load]);

  const execute = async (record: MesDisposition) => {
    try {
      await executeMesDisposition(record.id);
      message.success(
        record.dispositionType === 'REWORK' ? '处置已执行，已生成返工单' : '处置已执行',
      );
      await load();
    } catch {
      message.error('执行失败：处置状态或 NCR 状态不可执行');
    }
  };

  const columns: TableProps<MesDisposition>['columns'] = [
    { title: '处置单号', dataIndex: 'dispositionNo', key: 'dispositionNo', width: 170 },
    { title: 'NCR 编号', dataIndex: 'ncrNo', key: 'ncrNo', width: 170 },
    {
      title: '处置类型',
      dataIndex: 'dispositionType',
      key: 'dispositionType',
      width: 110,
      render: (value: string) => <Tag color="blue">{DISPOSITION_TYPE_LABEL[value] ?? value}</Tag>,
    },
    {
      title: '数量',
      dataIndex: 'quantity',
      key: 'quantity',
      width: 90,
      render: (value?: number | string) => formatQuantity(value),
    },
    { title: '原因', dataIndex: 'reason', key: 'reason', width: 200 },
    { title: '审批人', dataIndex: 'approvedBy', key: 'approvedBy', width: 110 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: string) => (
        <Tag color={value === 'EXECUTED' ? 'green' : value === 'CANCELLED' ? 'default' : 'orange'}>
          {DISPOSITION_STATUS_LABEL[value] ?? value}
        </Tag>
      ),
    },
    {
      title: '执行时间',
      dataIndex: 'executedAt',
      key: 'executedAt',
      width: 160,
      render: (value?: string) => formatTime(value),
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) =>
        canDisposition && record.status === 'CONFIRMED' ? (
          <Popconfirm
            title="确认执行该处置？"
            okText="执行"
            cancelText="取消"
            onConfirm={() => execute(record)}
          >
            <Button type="link" size="small">
              执行
            </Button>
          </Popconfirm>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="处置单号"
          style={{ width: 220 }}
          onSearch={(value) => setDispositionNo(value || undefined)}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 140 }}
          value={status}
          onChange={(value) => setStatus(value)}
          options={[
            { value: 'CONFIRMED', label: '已确认' },
            { value: 'EXECUTED', label: '已执行' },
            { value: 'CANCELLED', label: '已取消' },
          ]}
        />
      </Space>
      <Table<MesDisposition>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={records}
        scroll={{ x: 1200 }}
      />
    </Space>
  );
}

/** 返工面板 */
function ReworkPanel() {
  const [records, setRecords] = useState<MesReworkOrder[]>([]);
  const [loading, setLoading] = useState(false);
  const [reworkNo, setReworkNo] = useState<string>();
  const [status, setStatus] = useState<string>();
  const canRework = hasPermission('sys:mes:quality:rework');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRecords((await pageMesReworks({ reworkNo, status })).records);
    } finally {
      setLoading(false);
    }
  }, [reworkNo, status]);

  useEffect(() => {
    void load();
  }, [load]);

  const run = async (record: MesReworkOrder, action: 'start' | 'complete') => {
    try {
      if (action === 'start') {
        await startMesRework(record.id);
      } else {
        await completeMesRework(record.id);
      }
      message.success(action === 'start' ? '返工已开工' : '返工已完工');
      await load();
    } catch {
      message.error('操作失败：请确认返工单当前状态');
    }
  };

  const columns: TableProps<MesReworkOrder>['columns'] = [
    { title: '返工单号', dataIndex: 'reworkNo', key: 'reworkNo', width: 170 },
    { title: 'NCR 编号', dataIndex: 'ncrNo', key: 'ncrNo', width: 170 },
    { title: '工单编码', dataIndex: 'workOrderCode', key: 'workOrderCode', width: 140 },
    { title: '物料', dataIndex: 'itemName', key: 'itemName', width: 130 },
    { title: '批次号', dataIndex: 'batchNo', key: 'batchNo', width: 150 },
    {
      title: '数量',
      dataIndex: 'quantity',
      key: 'quantity',
      width: 90,
      render: (value?: number | string) => formatQuantity(value),
    },
    { title: '原因', dataIndex: 'reason', key: 'reason', width: 180 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: string) => (
        <Tag color={REWORK_STATUS_COLOR[value] ?? 'default'}>
          {REWORK_STATUS_LABEL[value] ?? value}
        </Tag>
      ),
    },
    {
      title: '开工时间',
      dataIndex: 'startedAt',
      key: 'startedAt',
      width: 160,
      render: (value?: string) => formatTime(value),
    },
    {
      title: '完工时间',
      dataIndex: 'completedAt',
      key: 'completedAt',
      width: 160,
      render: (value?: string) => formatTime(value),
    },
    {
      title: '操作',
      key: 'action',
      width: 110,
      render: (_, record) => {
        if (!canRework) {
          return null;
        }
        if (record.status === 'CREATED') {
          return (
            <Popconfirm
              title="确认开工？"
              okText="开工"
              cancelText="取消"
              onConfirm={() => run(record, 'start')}
            >
              <Button type="link" size="small">
                开工
              </Button>
            </Popconfirm>
          );
        }
        if (record.status === 'IN_PROGRESS') {
          return (
            <Popconfirm
              title="确认完工？"
              okText="完工"
              cancelText="取消"
              onConfirm={() => run(record, 'complete')}
            >
              <Button type="link" size="small">
                完工
              </Button>
            </Popconfirm>
          );
        }
        return null;
      },
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="返工单号"
          style={{ width: 220 }}
          onSearch={(value) => setReworkNo(value || undefined)}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 140 }}
          value={status}
          onChange={(value) => setStatus(value)}
          options={Object.entries(REWORK_STATUS_LABEL).map(([value, label]) => ({ value, label }))}
        />
      </Space>
      <Table<MesReworkOrder>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={records}
        scroll={{ x: 1500 }}
      />
    </Space>
  );
}

/** Xbar 控制图（子组均值与 UCL / CL / LCL） */
function SpcChart({ points, rule }: { points: MesSpcChartPoint[]; rule?: MesSpcRule }) {
  if (points.length === 0) {
    return <Empty description="暂无子组数据，录入满额子组后自动计算" />;
  }
  const width = 760;
  const height = 220;
  const pad = 36;
  const values = points.map((point) => toNumber(point.subgroupXbar) ?? 0);
  const cl = toNumber(rule?.centerLine) ?? average(values);
  const ucl = toNumber(rule?.ucl) ?? Math.max(...values);
  const lcl = toNumber(rule?.lcl) ?? Math.min(...values);
  const min = Math.min(lcl, ...values);
  const max = Math.max(ucl, ...values);
  const span = max - min || 1;
  const scaleX = (index: number) =>
    pad + (index * (width - pad * 2)) / Math.max(points.length - 1, 1);
  const scaleY = (value: number) => height - pad - ((value - min) / span) * (height - pad * 2);
  const linePath = values
    .map(
      (value, index) =>
        `${index === 0 ? 'M' : 'L'}${scaleX(index).toFixed(1)},${scaleY(value).toFixed(1)}`,
    )
    .join(' ');

  return (
    <div style={{ overflowX: 'auto' }}>
      <svg
        viewBox={`0 0 ${width} ${height}`}
        width="100%"
        height={height}
        role="img"
        aria-label="Xbar 控制图"
      >
        <line
          x1={pad}
          y1={scaleY(ucl)}
          x2={width - pad}
          y2={scaleY(ucl)}
          stroke="#ff4d4f"
          strokeDasharray="5 4"
        />
        <line
          x1={pad}
          y1={scaleY(cl)}
          x2={width - pad}
          y2={scaleY(cl)}
          stroke="#8c8c8c"
          strokeDasharray="3 3"
        />
        <line
          x1={pad}
          y1={scaleY(lcl)}
          x2={width - pad}
          y2={scaleY(lcl)}
          stroke="#ff4d4f"
          strokeDasharray="5 4"
        />
        <text x={pad + 4} y={scaleY(ucl) - 6} fontSize={11} fill="#ff4d4f">
          UCL {ucl.toFixed(2)}
        </text>
        <text x={pad + 4} y={scaleY(cl) - 6} fontSize={11} fill="#8c8c8c">
          CL {cl.toFixed(2)}
        </text>
        <text x={pad + 4} y={scaleY(lcl) - 6} fontSize={11} fill="#ff4d4f">
          LCL {lcl.toFixed(2)}
        </text>
        <path d={linePath} fill="none" stroke="#1677ff" strokeWidth={2} />
        {values.map((value, index) => (
          <circle
            key={points[index].subgroupNo}
            cx={scaleX(index)}
            cy={scaleY(value)}
            r={points[index].alertFlag === 1 ? 5 : 4}
            fill={points[index].alertFlag === 1 ? '#ff4d4f' : '#1677ff'}
          />
        ))}
      </svg>
    </div>
  );
}

/** SPC 面板 */
function SpcPanel() {
  const [rules, setRules] = useState<MesSpcRule[]>([]);
  const [samples, setSamples] = useState<MesSpcSample[]>([]);
  const [chartPoints, setChartPoints] = useState<MesSpcChartPoint[]>([]);
  const [plans, setPlans] = useState<MesInspectionPlan[]>([]);
  const [itemOptions, setItemOptions] = useState<MesInspectionPlanItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [ruleCode, setRuleCode] = useState<string>();
  const [selectedRuleId, setSelectedRuleId] = useState<string>();
  const [ruleOpen, setRuleOpen] = useState(false);
  const [sampleOpen, setSampleOpen] = useState(false);
  const [editingRule, setEditingRule] = useState<MesSpcRule | null>(null);
  const [ruleForm] = Form.useForm<MesSpcRuleInput>();
  const [sampleForm] = Form.useForm<{
    ruleId: string;
    subgroupNo: number;
    sampleSeq?: number;
    measuredValue: number;
  }>();
  const canSpc = hasPermission('sys:mes:quality:spc');

  const loadRules = useCallback(async () => {
    const page = await pageMesSpcRules({ ruleCode });
    setRules(page.records);
  }, [ruleCode]);

  const loadSamples = useCallback(async () => {
    if (!selectedRuleId) {
      setSamples([]);
      return;
    }
    setSamples((await pageMesSpcSamples({ ruleId: selectedRuleId })).records);
  }, [selectedRuleId]);

  const loadChart = useCallback(async () => {
    const rule = rules.find((item) => item.id === selectedRuleId);
    if (!rule) {
      setChartPoints([]);
      return;
    }
    setChartPoints(
      await fetchMesSpcChart({ planItemId: rule.planItemId, ruleId: rule.id, limit: 30 }),
    );
  }, [rules, selectedRuleId]);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      await Promise.all([loadRules(), loadSamples(), loadChart()]);
    } finally {
      setLoading(false);
    }
  }, [loadRules, loadSamples, loadChart]);

  useEffect(() => {
    void reload();
  }, [reload]);

  useEffect(() => {
    void (async () => {
      setPlans((await pageMesInspectionPlans({ status: 'ENABLED' })).records);
    })();
  }, []);

  const loadPlanItems = async (planId?: string) => {
    setItemOptions(planId ? ((await getMesInspectionPlan(planId)).items ?? []) : []);
  };

  const openRule = async (rule: MesSpcRule | null) => {
    setEditingRule(rule);
    ruleForm.resetFields();
    setItemOptions([]);
    if (rule) {
      ruleForm.setFieldsValue({
        id: rule.id,
        ruleCode: rule.ruleCode,
        ruleName: rule.ruleName,
        planItemId: rule.planItemId,
        operationCode: rule.operationCode,
        chartType: 'XBAR_R',
        subgroupSize: rule.subgroupSize,
        centerLine: toNumber(rule.centerLine),
        ucl: toNumber(rule.ucl),
        lcl: toNumber(rule.lcl),
        rUcl: toNumber(rule.rUcl),
        rLcl: toNumber(rule.rLcl),
        alertAction: rule.alertAction,
        status: rule.status,
        remark: rule.remark,
      });
    } else {
      ruleForm.setFieldsValue({
        chartType: 'XBAR_R',
        subgroupSize: 5,
        alertAction: 'WARN',
        status: 'ENABLED',
      });
    }
    setRuleOpen(true);
  };

  const submitRule = async () => {
    const values = await ruleForm.validateFields().catch(() => null);
    if (!values) {
      return;
    }
    try {
      await saveMesSpcRule({ ...values, id: editingRule?.id });
      message.success('SPC 规则已保存');
      setRuleOpen(false);
      await reload();
    } catch {
      message.error('保存失败：规则编码需唯一，且检验计划项必须有效');
    }
  };

  const openSample = () => {
    sampleForm.resetFields();
    sampleForm.setFieldsValue({
      ruleId: selectedRuleId ?? rules.find((item) => item.status === 'ENABLED')?.id,
      subgroupNo: 1,
      sampleSeq: 1,
      measuredValue: 0,
    });
    setSampleOpen(true);
  };

  const submitSample = async () => {
    const values = await sampleForm.validateFields().catch(() => null);
    if (!values) {
      return;
    }
    const rule = rules.find((item) => item.id === values.ruleId);
    if (!rule) {
      message.error('请选择有效的 SPC 规则');
      return;
    }
    try {
      await submitMesSpcSample({
        ruleId: values.ruleId,
        planItemId: rule.planItemId,
        subgroupNo: values.subgroupNo,
        sampleSeq: values.sampleSeq,
        measuredValue: values.measuredValue,
      });
      message.success('SPC 样本已录入');
      setSampleOpen(false);
      await reload();
    } catch {
      message.error('录入失败：检验项必须有效且与规则绑定项一致');
    }
  };

  const selectedRule = rules.find((item) => item.id === selectedRuleId);

  const ruleColumns: TableProps<MesSpcRule>['columns'] = [
    { title: '规则编码', dataIndex: 'ruleCode', key: 'ruleCode', width: 150 },
    { title: '规则名称', dataIndex: 'ruleName', key: 'ruleName', width: 160 },
    { title: '检验项 ID', dataIndex: 'planItemId', key: 'planItemId', width: 110 },
    { title: '工序', dataIndex: 'operationCode', key: 'operationCode', width: 110 },
    { title: '图类型', dataIndex: 'chartType', key: 'chartType', width: 100 },
    { title: '子组大小', dataIndex: 'subgroupSize', key: 'subgroupSize', width: 100 },
    {
      title: '控制限 (LCL / CL / UCL)',
      key: 'limits',
      width: 220,
      render: (_, record) =>
        `${formatQuantity(record.lcl)} / ${formatQuantity(record.centerLine)} / ${formatQuantity(record.ucl)}`,
    },
    {
      title: '命中处理',
      dataIndex: 'alertAction',
      key: 'alertAction',
      width: 100,
      render: (value: string) => (
        <Tag color={value === 'BLOCK' ? 'red' : 'orange'}>
          {value === 'BLOCK' ? '阻断' : '预警'}
        </Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (value: string) => (
        <Tag color={value === 'ENABLED' ? 'green' : 'default'}>
          {value === 'ENABLED' ? '启用' : '停用'}
        </Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) =>
        canSpc ? (
          <Button type="link" size="small" onClick={() => void openRule(record)}>
            编辑
          </Button>
        ) : null,
    },
  ];

  const sampleColumns: TableProps<MesSpcSample>['columns'] = [
    { title: '样本号', dataIndex: 'sampleNo', key: 'sampleNo', width: 160 },
    { title: '子组', dataIndex: 'subgroupNo', key: 'subgroupNo', width: 80 },
    { title: '序号', dataIndex: 'sampleSeq', key: 'sampleSeq', width: 80 },
    {
      title: '实测值',
      dataIndex: 'measuredValue',
      key: 'measuredValue',
      width: 100,
      render: (value?: number | string) => formatQuantity(value),
    },
    {
      title: '子组均值',
      dataIndex: 'subgroupXbar',
      key: 'subgroupXbar',
      width: 110,
      render: (value?: number | string) => formatQuantity(value),
    },
    {
      title: '子组极差',
      dataIndex: 'subgroupRange',
      key: 'subgroupRange',
      width: 110,
      render: (value?: number | string) => formatQuantity(value),
    },
    {
      title: '规则命中',
      dataIndex: 'alertFlag',
      key: 'alertFlag',
      width: 100,
      render: (value: number) => (value === 1 ? <Tag color="red">命中</Tag> : <Tag>在控</Tag>),
    },
    { title: '命中规则', dataIndex: 'alertRule', key: 'alertRule', width: 140 },
    {
      title: '采样时间',
      dataIndex: 'sampledAt',
      key: 'sampledAt',
      width: 160,
      render: (value?: string) => formatTime(value),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        size="small"
        title="Xbar-R 控制图"
        loading={loading}
        extra={
          <Space wrap>
            <Select
              allowClear
              placeholder="选择 SPC 规则"
              style={{ width: 260 }}
              value={selectedRuleId}
              onChange={(value) => setSelectedRuleId(value)}
              options={rules.map((rule) => ({
                value: rule.id,
                label: `${rule.ruleCode} ${rule.ruleName}`,
              }))}
            />
            {canSpc ? (
              <Button type="primary" onClick={openSample} disabled={!selectedRuleId}>
                录入样本
              </Button>
            ) : null}
          </Space>
        }
      >
        <SpcChart points={chartPoints} rule={selectedRule} />
      </Card>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="规则编码"
          style={{ width: 220 }}
          onSearch={(value) => setRuleCode(value || undefined)}
        />
        {canSpc ? (
          <Button type="primary" onClick={() => void openRule(null)}>
            新增 SPC 规则
          </Button>
        ) : null}
      </Space>
      <Table<MesSpcRule>
        rowKey="id"
        loading={loading}
        columns={ruleColumns}
        dataSource={rules}
        scroll={{ x: 1300 }}
        onRow={(record) => ({
          onClick: () => setSelectedRuleId(record.id),
          style: {
            cursor: 'pointer',
            background: record.id === selectedRuleId ? '#e6f4ff' : undefined,
          },
        })}
      />
      <Typography.Title level={5} style={{ margin: 0 }}>
        SPC 样本{selectedRule ? `（${selectedRule.ruleCode}）` : ''}
      </Typography.Title>
      <Table<MesSpcSample>
        rowKey="id"
        loading={loading}
        columns={sampleColumns}
        dataSource={samples}
        scroll={{ x: 1200 }}
        locale={{ emptyText: '请选择规则查看样本' }}
      />
      <Modal
        open={ruleOpen}
        title={editingRule ? '编辑 SPC 规则' : '新增 SPC 规则'}
        width={720}
        onOk={submitRule}
        onCancel={() => setRuleOpen(false)}
        okText="保存"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={ruleForm} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="ruleCode" label="规则编码（空则自动生成）">
                <Input maxLength={64} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="ruleName"
                label="规则名称"
                rules={[{ required: true, message: '请填写规则名称' }]}
              >
                <Input maxLength={128} />
              </Form.Item>
            </Col>
            {editingRule ? null : (
              <Col span={12}>
                <Form.Item label="检验计划">
                  <Select
                    showSearch
                    optionFilterProp="label"
                    placeholder="选择计划后加载检验项"
                    options={plans.map((plan) => ({
                      value: plan.id,
                      label: `${plan.planNo} ${plan.planName}`,
                    }))}
                    onChange={(value) => void loadPlanItems(value)}
                  />
                </Form.Item>
              </Col>
            )}
            <Col span={12}>
              {editingRule ? (
                <Form.Item name="planItemId" label="检验项 ID">
                  <Input disabled />
                </Form.Item>
              ) : (
                <Form.Item
                  name="planItemId"
                  label="检验项"
                  rules={[{ required: true, message: '请选择检验项' }]}
                >
                  <Select
                    showSearch
                    optionFilterProp="label"
                    options={itemOptions.map((item) => ({
                      value: String(item.id ?? ''),
                      label: `${item.itemNo} ${item.itemName}`,
                    }))}
                  />
                </Form.Item>
              )}
            </Col>
            <Col span={12}>
              <Form.Item name="operationCode" label="工序编码">
                <Input maxLength={64} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item
                name="subgroupSize"
                label="子组大小"
                rules={[{ required: true, message: '请填写子组大小' }]}
              >
                <InputNumber min={2} max={10} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="alertAction" label="命中处理">
                <Select
                  options={[
                    { value: 'WARN', label: '预警' },
                    { value: 'BLOCK', label: '阻断' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="centerLine" label="CL">
                <InputNumber style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="ucl" label="UCL">
                <InputNumber style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="lcl" label="LCL">
                <InputNumber style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="rUcl" label="极差 UCL">
                <InputNumber style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="rLcl" label="极差 LCL">
                <InputNumber style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="status" label="状态">
                <Select
                  options={[
                    { value: 'ENABLED', label: '启用' },
                    { value: 'DISABLED', label: '停用' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item name="remark" label="备注">
                <Input maxLength={255} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
      <Modal
        open={sampleOpen}
        title="录入 SPC 样本"
        onOk={submitSample}
        onCancel={() => setSampleOpen(false)}
        okText="提交"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={sampleForm} layout="vertical">
          <Form.Item
            name="ruleId"
            label="SPC 规则"
            rules={[{ required: true, message: '请选择规则' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              options={rules
                .filter((rule) => rule.status === 'ENABLED')
                .map((rule) => ({ value: rule.id, label: `${rule.ruleCode} ${rule.ruleName}` }))}
            />
          </Form.Item>
          <Space size="middle" wrap>
            <Form.Item
              name="subgroupNo"
              label="子组号"
              rules={[{ required: true, message: '请填写子组号' }]}
            >
              <InputNumber min={1} style={{ width: 120 }} />
            </Form.Item>
            <Form.Item name="sampleSeq" label="样本序号">
              <InputNumber min={1} style={{ width: 120 }} />
            </Form.Item>
            <Form.Item
              name="measuredValue"
              label="实测值"
              rules={[{ required: true, message: '请填写实测值' }]}
            >
              <InputNumber style={{ width: 160 }} />
            </Form.Item>
          </Space>
          <Alert
            type="info"
            showIcon
            message="子组满额后自动计算子组均值与极差，并按控制限判定、留痕。"
          />
        </Form>
      </Modal>
    </Space>
  );
}

/** 质量看板面板 */
function BoardPanel() {
  const [board, setBoard] = useState<MesQualityBoard | null>(null);
  const [loading, setLoading] = useState(false);
  const [boardDate, setBoardDate] = useState(dayjs());

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setBoard(await fetchMesQualityBoard({ boardDate: boardDate.format('YYYY-MM-DD') }));
    } finally {
      setLoading(false);
    }
  }, [boardDate]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Typography.Title level={5} style={{ margin: 0 }}>
          质量看板
        </Typography.Title>
        <DatePicker
          allowClear={false}
          value={boardDate}
          onChange={(value) => setBoardDate(value ?? dayjs())}
        />
      </Space>
      <Card loading={loading}>
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} md={6}>
            <Statistic title="当日检验任务" value={board?.inspectionTaskCount ?? 0} />
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Statistic title="待检任务" value={board?.pendingTaskCount ?? 0} />
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Statistic
              title="不合格任务"
              value={board?.failedTaskCount ?? 0}
              valueStyle={{ color: '#cf1322' }}
            />
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Statistic
              title="首件通过率"
              value={board?.firstArticlePassRate ?? 0}
              suffix="%"
              precision={2}
            />
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Statistic
              title="未关闭 NCR"
              value={board?.ncrOpenCount ?? 0}
              valueStyle={{ color: '#d46b08' }}
            />
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Statistic title="已关闭 NCR" value={board?.ncrClosedCount ?? 0} />
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Statistic title="处置单数" value={board?.dispositionCount ?? 0} />
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Statistic title="未完成返工单" value={board?.reworkOpenCount ?? 0} />
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Statistic
              title="SPC 命中样本"
              value={board?.spcAlertCount ?? 0}
              valueStyle={{ color: '#cf1322' }}
            />
          </Col>
        </Row>
      </Card>
    </Space>
  );
}

/** 车间质量页 */
export default function QualityPage({ initialTab = 'plan' }: { initialTab?: QualityTab }) {
  const [tab, setTab] = useState<QualityTab>(initialTab);

  useEffect(() => {
    setTab(initialTab);
  }, [initialTab]);

  const items: TabsProps['items'] = [
    { key: 'plan', label: '检验计划', children: <PlanPanel /> },
    {
      key: 'first-article',
      label: '首件检验',
      children: <InspectionPanel taskType="FIRST_ARTICLE" withGate />,
    },
    { key: 'ipqc', label: 'IPQC 巡检', children: <InspectionPanel taskType="IPQC" /> },
    { key: 'ncr', label: '不良品管理', children: <NcrPanel /> },
    { key: 'disposition', label: '处置管理', children: <DispositionPanel /> },
    { key: 'rework', label: '返工管理', children: <ReworkPanel /> },
    { key: 'spc', label: 'SPC 控制', children: <SpcPanel /> },
    { key: 'board', label: '质量看板', children: <BoardPanel /> },
  ];

  return (
    <Tabs
      activeKey={tab}
      onChange={(key) => setTab(key as QualityTab)}
      items={items}
      destroyInactiveTabPane
    />
  );
}
