// ERP 计量器具页：量具台账、校准计划与校准记录
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  ENABLED_STATUS_LABEL,
  QC_RESULT_LABEL,
  calibrate,
  createCalibrationPlan,
  createTool,
  pageCalibrationPlans,
  pageCalibrationRecords,
  pageTools,
  qcStatusColor,
  updateCalibrationPlan,
  updateTool,
  type CalibrationPlanView,
  type CalibrationRecordView,
  type MeasuringToolView,
} from '../api/qc';
import { hasPermission } from '../utils/permission';

/** 量具表单值 */
interface ToolFormValues {
  toolNo: string;
  name: string;
  toolType?: string;
  precisionValue?: string;
  calibrationCycleMonths?: number;
  lastCalibrationDate?: Dayjs;
  nextCalibrationDate?: Dayjs;
  status: string;
  remark?: string;
}

/** 校准计划表单值 */
interface PlanFormValues {
  toolNo: string;
  cycleMonths?: number;
  nextDate: Dayjs;
  status: string;
  remark?: string;
}

/** 校准记录表单值 */
interface RecordFormValues {
  toolNo: string;
  planId?: number;
  calibrationDate: Dayjs;
  result: string;
  certificateNo?: string;
  remark?: string;
}

/** 量具下拉 */
function ToolSelect({ tools }: { tools: MeasuringToolView[] }) {
  return (
    <Select
      showSearch
      optionFilterProp="label"
      placeholder="选择量具"
      style={{ width: 210 }}
      options={tools.map((tool) => ({
        value: tool.toolNo,
        label: `${tool.toolNo} ${tool.name}`,
      }))}
    />
  );
}

/** 量具弹窗 */
function ToolModal({
  open,
  initial,
  onCancel,
  onSaved,
}: {
  open: boolean;
  initial: MeasuringToolView | null;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<ToolFormValues>();

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    if (initial) {
      form.setFieldsValue({
        toolNo: initial.toolNo,
        name: initial.name,
        toolType: initial.toolType,
        precisionValue: initial.precisionValue,
        calibrationCycleMonths: initial.calibrationCycleMonths,
        lastCalibrationDate: initial.lastCalibrationDate
          ? dayjs(initial.lastCalibrationDate)
          : undefined,
        nextCalibrationDate: initial.nextCalibrationDate
          ? dayjs(initial.nextCalibrationDate)
          : undefined,
        status: initial.status,
        remark: initial.remark,
      });
    } else {
      form.setFieldsValue({
        calibrationCycleMonths: 12,
        status: 'ACTIVE',
        lastCalibrationDate: dayjs(),
      });
    }
  }, [open, initial, form]);

  const handleOk = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    const input = {
      toolNo: values.toolNo,
      name: values.name,
      toolType: values.toolType,
      precisionValue: values.precisionValue,
      calibrationCycleMonths: Number(values.calibrationCycleMonths ?? 0),
      lastCalibrationDate: values.lastCalibrationDate?.format('YYYY-MM-DD'),
      nextCalibrationDate: values.nextCalibrationDate?.format('YYYY-MM-DD'),
      status: values.status,
      remark: values.remark,
    };
    if (initial) {
      await updateTool(initial.toolNo, input);
      message.success('量具已更新');
    } else {
      await createTool(input);
      message.success('量具已创建');
    }
    onSaved();
  };

  return (
    <Modal
      open={open}
      title={initial ? '编辑计量器具' : '新增计量器具'}
      width={680}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnHidden
    >
      <Form form={form} layout="vertical">
        <Space size="middle" wrap>
          <Form.Item name="toolNo" label="量具编号" rules={[{ required: true, message: '请输入量具编号' }]}>
            <Input maxLength={64} disabled={initial !== null} style={{ width: 190 }} />
          </Form.Item>
          <Form.Item name="name" label="量具名称" rules={[{ required: true, message: '请输入量具名称' }]}>
            <Input maxLength={128} style={{ width: 220 }} />
          </Form.Item>
          <Form.Item name="toolType" label="量具类型">
            <Input maxLength={64} style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="precisionValue" label="精度值">
            <Input maxLength={64} style={{ width: 160 }} />
          </Form.Item>
          <Form.Item
            name="calibrationCycleMonths"
            label="校准周期（月）"
            rules={[{ required: true, message: '请输入校准周期' }]}
          >
            <InputNumber min={1} precision={0} style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select
              style={{ width: 130 }}
              options={Object.entries(ENABLED_STATUS_LABEL).map(([value, label]) => ({
                value,
                label,
              }))}
            />
          </Form.Item>
          <Form.Item name="lastCalibrationDate" label="上次校准日期">
            <DatePicker format="YYYY-MM-DD" />
          </Form.Item>
          <Form.Item name="nextCalibrationDate" label="下次校准日期">
            <DatePicker format="YYYY-MM-DD" />
          </Form.Item>
        </Space>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** 校准计划弹窗 */
function PlanModal({
  open,
  initial,
  tools,
  onCancel,
  onSaved,
}: {
  open: boolean;
  initial: CalibrationPlanView | null;
  tools: MeasuringToolView[];
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<PlanFormValues>();

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    if (initial) {
      form.setFieldsValue({
        toolNo: initial.toolNo,
        cycleMonths: initial.cycleMonths,
        nextDate: dayjs(initial.nextDate),
        status: initial.status,
        remark: initial.remark,
      });
    } else {
      form.setFieldsValue({ cycleMonths: 12, nextDate: dayjs().add(1, 'year'), status: 'ACTIVE' });
    }
  }, [open, initial, form]);

  const handleOk = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    const input = {
      toolNo: values.toolNo,
      cycleMonths: Number(values.cycleMonths ?? 0),
      nextDate: values.nextDate.format('YYYY-MM-DD'),
      status: values.status,
      remark: values.remark,
    };
    if (initial) {
      await updateCalibrationPlan(initial.id, input);
      message.success('校准计划已更新');
    } else {
      await createCalibrationPlan(input);
      message.success('校准计划已创建');
    }
    onSaved();
  };

  return (
    <Modal
      open={open}
      title={initial ? '编辑校准计划' : '新增校准计划'}
      width={620}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnHidden
    >
      <Form form={form} layout="vertical">
        <Space size="middle" wrap>
          <Form.Item name="toolNo" label="量具" rules={[{ required: true, message: '请选择量具' }]}>
            <ToolSelect tools={tools} />
          </Form.Item>
          <Form.Item
            name="cycleMonths"
            label="校准周期（月）"
            rules={[{ required: true, message: '请输入校准周期' }]}
          >
            <InputNumber min={1} precision={0} style={{ width: 140 }} />
          </Form.Item>
          <Form.Item
            name="nextDate"
            label="下次校准日期"
            rules={[{ required: true, message: '请选择下次校准日期' }]}
          >
            <DatePicker format="YYYY-MM-DD" />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select
              style={{ width: 130 }}
              options={Object.entries(ENABLED_STATUS_LABEL).map(([value, label]) => ({
                value,
                label,
              }))}
            />
          </Form.Item>
        </Space>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** 校准登记弹窗 */
function CalibrateModal({
  open,
  tools,
  plans,
  onCancel,
  onSaved,
}: {
  open: boolean;
  tools: MeasuringToolView[];
  plans: CalibrationPlanView[];
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<RecordFormValues>();
  const toolNo = Form.useWatch('toolNo', form);

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue({ calibrationDate: dayjs(), result: 'PASS' });
  }, [open, form]);

  const handleOk = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await calibrate({
      toolNo: values.toolNo,
      planId: values.planId,
      calibrationDate: values.calibrationDate.format('YYYY-MM-DD'),
      result: values.result,
      certificateNo: values.certificateNo,
      remark: values.remark,
    });
    message.success(values.result === 'PASS' ? '校准合格，已顺延下次校准日期' : '校准不合格，量具状态需复核');
    onSaved();
  };

  const planOptions = plans
    .filter((plan) => (toolNo ? plan.toolNo === toolNo : true))
    .map((plan) => ({
      value: plan.id,
      label: `${plan.planNo}（下次 ${plan.nextDate}）`,
    }));

  return (
    <Modal open={open} title="登记校准记录" width={640} onOk={handleOk} onCancel={onCancel} destroyOnHidden>
      <Form form={form} layout="vertical">
        <Space size="middle" wrap>
          <Form.Item name="toolNo" label="量具" rules={[{ required: true, message: '请选择量具' }]}>
            <ToolSelect tools={tools} />
          </Form.Item>
          <Form.Item name="planId" label="校准计划">
            <Select allowClear placeholder="选择计划" style={{ width: 230 }} options={planOptions} />
          </Form.Item>
          <Form.Item
            name="calibrationDate"
            label="校准日期"
            rules={[{ required: true, message: '请选择校准日期' }]}
          >
            <DatePicker format="YYYY-MM-DD" />
          </Form.Item>
          <Form.Item name="result" label="校准结论" rules={[{ required: true }]}>
            <Select
              style={{ width: 130 }}
              options={Object.entries(QC_RESULT_LABEL).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
          <Form.Item name="certificateNo" label="校准证书号">
            <Input maxLength={64} style={{ width: 200 }} />
          </Form.Item>
        </Space>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** 量具台账页签 */
function ToolTab({ notify }: { notify: () => void }) {
  const [data, setData] = useState<MeasuringToolView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [version, setVersion] = useState(0);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<MeasuringToolView | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageTools({ size: 50, keyword, status });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword, status]);

  useEffect(() => {
    void load();
  }, [load, version]);

  const columns: TableProps<MeasuringToolView>['columns'] = [
    { title: '量具编号', dataIndex: 'toolNo', key: 'toolNo', width: 140 },
    { title: '量具名称', dataIndex: 'name', key: 'name' },
    { title: '类型', dataIndex: 'toolType', key: 'toolType', width: 120 },
    { title: '精度值', dataIndex: 'precisionValue', key: 'precisionValue', width: 130 },
    {
      title: '校准周期（月）',
      dataIndex: 'calibrationCycleMonths',
      key: 'calibrationCycleMonths',
      width: 130,
    },
    { title: '上次校准', dataIndex: 'lastCalibrationDate', key: 'lastCalibrationDate', width: 120 },
    { title: '下次校准', dataIndex: 'nextCalibrationDate', key: 'nextCalibrationDate', width: 120 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value) => (
        <Tag color={qcStatusColor(String(value))}>
          {ENABLED_STATUS_LABEL[String(value)] ?? value}
        </Tag>
      ),
    },
    { title: '备注', dataIndex: 'remark', key: 'remark' },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) =>
        hasPermission('sys:erp:qc:metrology:update') ? (
          <Button
            type="link"
            size="small"
            onClick={() => {
              setEditing(record);
              setModalOpen(true);
            }}
          >
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
          placeholder="量具编号 / 名称"
          style={{ width: 240 }}
          onSearch={(value) => setKeyword(value)}
        />
        <Button onClick={() => setStatus(undefined)} type={status ? 'default' : 'primary'}>
          全部
        </Button>
        {['ACTIVE', 'DISABLED'].map((value) => (
          <Button
            key={value}
            onClick={() => setStatus(value)}
            type={status === value ? 'primary' : 'default'}
          >
            {ENABLED_STATUS_LABEL[value] ?? value}
          </Button>
        ))}
        {hasPermission('sys:erp:qc:metrology:add') ? (
          <Button
            type="primary"
            onClick={() => {
              setEditing(null);
              setModalOpen(true);
            }}
          >
            新增量具
          </Button>
        ) : null}
      </Space>
      <Table<MeasuringToolView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        pagination={false}
      />
      <ToolModal
        open={modalOpen}
        initial={editing}
        onCancel={() => setModalOpen(false)}
        onSaved={() => {
          setModalOpen(false);
          setVersion((value) => value + 1);
          notify();
        }}
      />
    </Space>
  );
}

/** 校准计划页签 */
function PlanTab({
  tools,
  notify,
}: {
  tools: MeasuringToolView[];
  notify: () => void;
}) {
  const [data, setData] = useState<CalibrationPlanView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [version, setVersion] = useState(0);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<CalibrationPlanView | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageCalibrationPlans({ size: 50, keyword, status });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword, status]);

  useEffect(() => {
    void load();
  }, [load, version]);

  const columns: TableProps<CalibrationPlanView>['columns'] = [
    { title: '计划号', dataIndex: 'planNo', key: 'planNo', width: 170 },
    { title: '量具编号', dataIndex: 'toolNo', key: 'toolNo', width: 150 },
    { title: '校准周期（月）', dataIndex: 'cycleMonths', key: 'cycleMonths', width: 130 },
    { title: '下次校准日期', dataIndex: 'nextDate', key: 'nextDate', width: 130 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value) => (
        <Tag color={qcStatusColor(String(value))}>
          {ENABLED_STATUS_LABEL[String(value)] ?? value}
        </Tag>
      ),
    },
    { title: '备注', dataIndex: 'remark', key: 'remark' },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) =>
        hasPermission('sys:erp:qc:metrology:update') ? (
          <Button
            type="link"
            size="small"
            onClick={() => {
              setEditing(record);
              setModalOpen(true);
            }}
          >
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
          placeholder="计划号 / 量具编号"
          style={{ width: 240 }}
          onSearch={(value) => setKeyword(value)}
        />
        <Button onClick={() => setStatus(undefined)} type={status ? 'default' : 'primary'}>
          全部
        </Button>
        {['ACTIVE', 'DISABLED'].map((value) => (
          <Button
            key={value}
            onClick={() => setStatus(value)}
            type={status === value ? 'primary' : 'default'}
          >
            {ENABLED_STATUS_LABEL[value] ?? value}
          </Button>
        ))}
        {hasPermission('sys:erp:qc:metrology:add') ? (
          <Button
            type="primary"
            onClick={() => {
              setEditing(null);
              setModalOpen(true);
            }}
          >
            新增校准计划
          </Button>
        ) : null}
      </Space>
      <Table<CalibrationPlanView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        pagination={false}
      />
      <PlanModal
        open={modalOpen}
        initial={editing}
        tools={tools}
        onCancel={() => setModalOpen(false)}
        onSaved={() => {
          setModalOpen(false);
          setVersion((value) => value + 1);
          notify();
        }}
      />
    </Space>
  );
}

/** 校准记录页签 */
function RecordTab({
  tools,
  plans,
  ticket,
}: {
  tools: MeasuringToolView[];
  plans: CalibrationPlanView[];
  ticket: number;
}) {
  const [data, setData] = useState<CalibrationRecordView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [result, setResult] = useState<string | undefined>();
  const [version, setVersion] = useState(0);
  const [modalOpen, setModalOpen] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageCalibrationRecords({ size: 50, keyword, status: result });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword, result]);

  useEffect(() => {
    void load();
  }, [load, version, ticket]);

  const columns: TableProps<CalibrationRecordView>['columns'] = [
    { title: '校准记录号', dataIndex: 'recordNo', key: 'recordNo', width: 170 },
    { title: '量具编号', dataIndex: 'toolNo', key: 'toolNo', width: 150 },
    { title: '校准日期', dataIndex: 'calibrationDate', key: 'calibrationDate', width: 120 },
    {
      title: '校准结论',
      dataIndex: 'result',
      key: 'result',
      width: 110,
      render: (value) => (
        <Tag color={String(value) === 'PASS' ? 'green' : 'red'}>
          {QC_RESULT_LABEL[String(value)] ?? value}
        </Tag>
      ),
    },
    { title: '证书号', dataIndex: 'certificateNo', key: 'certificateNo', width: 160 },
    { title: '备注', dataIndex: 'remark', key: 'remark' },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="校准记录号 / 量具编号"
          style={{ width: 250 }}
          onSearch={(value) => setKeyword(value)}
        />
        <Button onClick={() => setResult(undefined)} type={result ? 'default' : 'primary'}>
          全部
        </Button>
        {['PASS', 'FAIL'].map((value) => (
          <Button
            key={value}
            onClick={() => setResult(value)}
            type={result === value ? 'primary' : 'default'}
          >
            {QC_RESULT_LABEL[value] ?? value}
          </Button>
        ))}
        {hasPermission('sys:erp:qc:metrology:calibrate') ? (
          <Button type="primary" onClick={() => setModalOpen(true)}>
            登记校准
          </Button>
        ) : null}
      </Space>
      <Table<CalibrationRecordView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        pagination={false}
      />
      <CalibrateModal
        open={modalOpen}
        tools={tools}
        plans={plans}
        onCancel={() => setModalOpen(false)}
        onSaved={() => {
          setModalOpen(false);
          setVersion((value) => value + 1);
        }}
      />
    </Space>
  );
}

/** 计量器具页 */
export default function QcMetrologyPage() {
  const [tab, setTab] = useState('tool');
  const [tools, setTools] = useState<MeasuringToolView[]>([]);
  const [plans, setPlans] = useState<CalibrationPlanView[]>([]);
  const [ticket, setTicket] = useState(0);

  const loadOptions = useCallback(async () => {
    const [toolPage, planPage] = await Promise.all([
      pageTools({ size: 200 }),
      pageCalibrationPlans({ size: 200 }),
    ]);
    setTools(toolPage.records);
    setPlans(planPage.records);
  }, []);

  useEffect(() => {
    void loadOptions();
  }, [loadOptions, ticket]);

  return (
    <Tabs
      activeKey={tab}
      onChange={setTab}
      items={[
        {
          key: 'tool',
          label: '量具台账',
          children: (
            <ToolTab
              notify={() => {
                setTicket((value) => value + 1);
              }}
            />
          ),
        },
        {
          key: 'plan',
          label: '校准计划',
          children: (
            <PlanTab
              tools={tools}
              notify={() => {
                setTicket((value) => value + 1);
              }}
            />
          ),
        },
        { key: 'record', label: '校准记录', children: <RecordTab tools={tools} plans={plans} ticket={ticket} /> },
      ]}
    />
  );
}
