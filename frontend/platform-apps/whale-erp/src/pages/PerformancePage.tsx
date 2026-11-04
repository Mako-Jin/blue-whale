// ERP 绩效页：绩效周期、指标、考核评分与关闭
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  HR_STATUS_LABEL,
  addKpi,
  appraise,
  closePerformanceCycle,
  createPerformanceCycle,
  getPerformanceCycle,
  hrStatusColor,
  pageEmployees,
  pagePerformanceCycles,
  type AppraisalView,
  type EmployeeView,
  type KpiView,
  type PerformanceCycleView,
} from '../api/hr';
import { hasPermission } from '../utils/permission';
import { quantity } from '../utils/scm';

/** 周期表单值 */
interface CycleFormValues {
  name: string;
  month: Dayjs;
  startDate: Dayjs;
  endDate: Dayjs;
  remark?: string;
}

/** 指标表单值 */
interface KpiFormValues {
  name: string;
  weight?: number;
  targetValue?: number;
  unit?: string;
  remark?: string;
}

/** 考核表单值 */
interface AppraisalFormValues {
  employeeNo: string;
  kpiId: number;
  score?: number;
  remark?: string;
}

/** 新建周期弹窗 */
function CycleModal({
  open,
  onCancel,
  onSaved,
}: {
  open: boolean;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<CycleFormValues>();
  const [month, setMonth] = useState<Dayjs>(dayjs());

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    const current = dayjs();
    setMonth(current);
    form.setFieldsValue({
      month: current,
      name: `${current.format('YYYY-MM')} 绩效`,
      startDate: current.startOf('month'),
      endDate: current.endOf('month'),
    });
  }, [open, form]);

  const handleOk = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await createPerformanceCycle({
      name: values.name,
      period: values.month.format('YYYY-MM'),
      startDate: values.startDate.format('YYYY-MM-DD'),
      endDate: values.endDate.format('YYYY-MM-DD'),
      remark: values.remark,
    });
    message.success('绩效周期已创建');
    onSaved();
  };

  return (
    <Modal open={open} title="新建绩效周期" width={560} onOk={handleOk} onCancel={onCancel} destroyOnHidden>
      <Form form={form} layout="vertical">
        <Form.Item name="name" label="周期名称" rules={[{ required: true, message: '请输入周期名称' }]}>
          <Input maxLength={64} />
        </Form.Item>
        <Space size="middle" wrap>
          <Form.Item
            name="month"
            label="考核期间"
            rules={[{ required: true, message: '请选择期间' }]}
          >
            <DatePicker
              picker="month"
              format="YYYY-MM"
              onChange={(value) => {
                if (!value) return;
                setMonth(value);
                form.setFieldsValue({
                  startDate: value.startOf('month'),
                  endDate: value.endOf('month'),
                });
              }}
            />
          </Form.Item>
          <Form.Item name="startDate" label="开始日期" rules={[{ required: true }]}>
            <DatePicker format="YYYY-MM-DD" disabledDate={(date) => date.isAfter(month.endOf('month'))} />
          </Form.Item>
          <Form.Item name="endDate" label="结束日期" rules={[{ required: true }]}>
            <DatePicker format="YYYY-MM-DD" disabledDate={(date) => date.isBefore(month.startOf('month'))} />
          </Form.Item>
        </Space>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** 新增指标弹窗 */
function KpiModal({
  open,
  onCancel,
  onSaved,
}: {
  open: boolean;
  onCancel: () => void;
  onSaved: (values: KpiFormValues) => Promise<void>;
}) {
  const [form] = Form.useForm<KpiFormValues>();

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue({ weight: 10, targetValue: 100 });
  }, [open, form]);

  const handleOk = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await onSaved(values);
  };

  return (
    <Modal open={open} title="新增绩效指标" onOk={handleOk} onCancel={onCancel} destroyOnHidden>
      <Form form={form} layout="vertical">
        <Form.Item name="name" label="指标名称" rules={[{ required: true, message: '请输入指标名称' }]}>
          <Input maxLength={64} />
        </Form.Item>
        <Space size="middle" wrap>
          <Form.Item name="weight" label="权重(%)">
            <InputNumber min={0} max={100} precision={2} style={{ width: 120 }} />
          </Form.Item>
          <Form.Item name="targetValue" label="目标值">
            <InputNumber min={0} precision={2} style={{ width: 120 }} />
          </Form.Item>
          <Form.Item name="unit" label="单位">
            <Input maxLength={16} style={{ width: 100 }} />
          </Form.Item>
        </Space>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** 考核弹窗 */
function AppraisalModal({
  open,
  kpis,
  employees,
  onCancel,
  onSaved,
}: {
  open: boolean;
  kpis: KpiView[];
  employees: EmployeeView[];
  onCancel: () => void;
  onSaved: (values: AppraisalFormValues) => Promise<void>;
}) {
  const [form] = Form.useForm<AppraisalFormValues>();

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue({ score: 80 });
  }, [open, form]);

  const handleOk = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await onSaved(values);
  };

  return (
    <Modal open={open} title="录入考核结果" width={560} onOk={handleOk} onCancel={onCancel} destroyOnHidden>
      <Form form={form} layout="vertical">
        <Space size="middle" wrap>
          <Form.Item name="employeeNo" label="员工" rules={[{ required: true, message: '请选择员工' }]}>
            <SelectEmployeeBasic employees={employees} />
          </Form.Item>
          <Form.Item name="kpiId" label="指标" rules={[{ required: true, message: '请选择指标' }]}>
            <SelectKpiBasic kpis={kpis} />
          </Form.Item>
          <Form.Item
            name="score"
            label="得分"
            rules={[{ required: true, message: '请输入得分' }]}
          >
            <InputNumber min={0} max={100} precision={2} style={{ width: 120 }} />
          </Form.Item>
        </Space>
        <Form.Item name="remark" label="评语">
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** 员工下拉 */
function SelectEmployeeBasic({ employees }: { employees: EmployeeView[] }) {
  return (
    <Select
      showSearch
      allowClear
      optionFilterProp="label"
      placeholder="选择员工"
      style={{ width: 220 }}
      options={employees.map((employee) => ({
        value: employee.employeeNo,
        label: `${employee.employeeNo} ${employee.name}`,
      }))}
    />
  );
}

/** 指标下拉 */
function SelectKpiBasic({ kpis }: { kpis: KpiView[] }) {
  return (
    <Select
      allowClear
      placeholder="选择指标"
      style={{ width: 220 }}
      options={kpis.map((kpi) => ({
        value: kpi.id,
        label: `${kpi.name}（权重 ${kpi.weight}%）`,
      }))}
    />
  );
}

/** 绩效页 */
export default function PerformancePage() {
  const [data, setData] = useState<PerformanceCycleView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [version, setVersion] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);
  const [detail, setDetail] = useState<PerformanceCycleView | null>(null);
  const [kpiOpen, setKpiOpen] = useState(false);
  const [appraisalOpen, setAppraisalOpen] = useState(false);
  const [employees, setEmployees] = useState<EmployeeView[]>([]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pagePerformanceCycles({ size: 50, keyword, status });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword, status]);

  useEffect(() => {
    void load();
  }, [load, version]);

  const refreshDetail = async (id: number) => {
    setDetail(await getPerformanceCycle(id));
  };

  const handleAddKpi = async (values: KpiFormValues) => {
    if (!detail) return;
    await addKpi(detail.id, {
      name: values.name,
      weight: Number(values.weight ?? 0),
      targetValue: Number(values.targetValue ?? 0),
      unit: values.unit,
      remark: values.remark,
    });
    message.success('绩效指标已新增');
    setKpiOpen(false);
    await refreshDetail(detail.id);
    setVersion((value) => value + 1);
  };

  const openAppraisal = async () => {
    if (!detail) return;
    if (!employees.length) {
      const page = await pageEmployees({ size: 200, status: 'ACTIVE' });
      setEmployees(page.records);
    }
    setAppraisalOpen(true);
  };

  const handleAppraise = async (values: AppraisalFormValues) => {
    if (!detail) return;
    await appraise(detail.id, {
      employeeNo: values.employeeNo,
      kpiId: values.kpiId,
      score: Number(values.score ?? 0),
      remark: values.remark,
    });
    message.success('考核结果已保存');
    setAppraisalOpen(false);
    await refreshDetail(detail.id);
    setVersion((value) => value + 1);
  };

  const handleClose = async (id: number) => {
    await closePerformanceCycle(id);
    message.success('绩效周期已关闭');
    if (detail?.id === id) {
      await refreshDetail(id);
    }
    setVersion((value) => value + 1);
  };

  const columns: TableProps<PerformanceCycleView>['columns'] = [
    { title: '周期编号', dataIndex: 'cycleNo', key: 'cycleNo', width: 170 },
    { title: '周期名称', dataIndex: 'name', key: 'name' },
    { title: '考核期间', dataIndex: 'period', key: 'period', width: 110 },
    { title: '开始日期', dataIndex: 'startDate', key: 'startDate', width: 120 },
    { title: '结束日期', dataIndex: 'endDate', key: 'endDate', width: 120 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value) => (
        <Tag color={hrStatusColor(String(value))}>{HR_STATUS_LABEL[String(value)] ?? value}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_, record) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => void refreshDetail(record.id)}>
            查看
          </Button>
          {record.status === 'IN_PROGRESS' && hasPermission('sys:erp:hr:performance:close') ? (
            <Popconfirm title="关闭后不可再录入指标与考核，确认？" onConfirm={() => handleClose(record.id)}>
              <Button type="link" size="small">
                关闭
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const kpiColumns: TableProps<KpiView>['columns'] = [
    { title: '指标名称', dataIndex: 'name', key: 'name' },
    { title: '权重(%)', dataIndex: 'weight', key: 'weight', width: 100, render: quantity },
    { title: '目标值', dataIndex: 'targetValue', key: 'targetValue', width: 110, render: quantity },
    { title: '单位', dataIndex: 'unit', key: 'unit', width: 90 },
  ];

  const appraisalColumns: TableProps<AppraisalView>['columns'] = [
    { title: '员工编号', dataIndex: 'employeeNo', key: 'employeeNo', width: 120 },
    { title: '员工姓名', dataIndex: 'employeeName', key: 'employeeName', width: 110 },
    { title: '指标', dataIndex: 'kpiName', key: 'kpiName' },
    { title: '得分', dataIndex: 'score', key: 'score', width: 90, render: quantity },
    {
      title: '等级',
      dataIndex: 'grade',
      key: 'grade',
      width: 80,
      render: (value) => <Tag color="blue">{value}</Tag>,
    },
    { title: '评语', dataIndex: 'remark', key: 'remark' },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="周期编号 / 名称"
          style={{ width: 240 }}
          onSearch={(value) => setKeyword(value)}
        />
        <Button onClick={() => setStatus(undefined)} type={status ? 'default' : 'primary'}>
          全部
        </Button>
        {['DRAFT', 'IN_PROGRESS', 'CLOSED'].map((value) => (
          <Button
            key={value}
            onClick={() => setStatus(value)}
            type={status === value ? 'primary' : 'default'}
          >
            {HR_STATUS_LABEL[value] ?? value}
          </Button>
        ))}
        {hasPermission('sys:erp:hr:performance:create') ? (
          <Button type="primary" onClick={() => setCreateOpen(true)}>
            新建绩效周期
          </Button>
        ) : null}
      </Space>
      <Table<PerformanceCycleView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        pagination={false}
      />
      <CycleModal
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onSaved={() => {
          setCreateOpen(false);
          setVersion((value) => value + 1);
        }}
      />
      <Modal
        open={detail !== null}
        title={detail ? `绩效周期 ${detail.cycleNo}` : '绩效周期详情'}
        footer={null}
        width={1000}
        onCancel={() => setDetail(null)}
        destroyOnHidden
      >
        {detail ? (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Space size="large" wrap>
              <span>名称：{detail.name}</span>
              <span>期间：{detail.period}</span>
              <span>状态：{HR_STATUS_LABEL[detail.status] ?? detail.status}</span>
              {detail.status !== 'CLOSED' && hasPermission('sys:erp:hr:performance:appraise') ? (
                <Space>
                  <Button size="small" onClick={() => setKpiOpen(true)}>
                    新增指标
                  </Button>
                  <Button size="small" type="primary" onClick={() => void openAppraisal()}>
                    录入考核
                  </Button>
                </Space>
              ) : null}
            </Space>
            <Table<KpiView>
              rowKey="id"
              size="small"
              title={() => '绩效指标'}
              columns={kpiColumns}
              dataSource={detail.kpis ?? []}
              pagination={false}
            />
            <Table<AppraisalView>
              rowKey="id"
              size="small"
              title={() => '考核结果'}
              columns={appraisalColumns}
              dataSource={detail.appraisals ?? []}
              pagination={false}
            />
          </Space>
        ) : null}
      </Modal>
      <KpiModal open={kpiOpen} onCancel={() => setKpiOpen(false)} onSaved={handleAddKpi} />
      <AppraisalModal
        open={appraisalOpen}
        kpis={detail?.kpis ?? []}
        employees={employees}
        onCancel={() => setAppraisalOpen(false)}
        onSaved={handleAppraise}
      />
    </Space>
  );
}
