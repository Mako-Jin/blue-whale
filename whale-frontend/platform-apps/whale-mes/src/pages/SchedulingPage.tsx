// MES 有限产能排程页：生成草稿 / 筛选计划 / 查看时段 / 锁定与取消
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  DatePicker,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import type { Dayjs } from 'dayjs';
import {
  cancelMesSchedulePlan,
  generateMesSchedule,
  getMesSchedulePlan,
  lockMesSchedulePlan,
  pageMesSchedulePlans,
  type MesSchedulePlan,
  type MesScheduleSlot,
  type MesScheduleStatus,
} from '../api/mes';
import { hasPermission } from '../utils/permission';

/** 计划状态文案 */
const STATUS_LABEL: Record<MesScheduleStatus, string> = {
  DRAFT: '草稿',
  LOCKED: '已锁定',
  CANCELLED: '已取消',
};

/** 计划状态颜色 */
const STATUS_COLOR: Record<MesScheduleStatus, string> = {
  DRAFT: 'default',
  LOCKED: 'green',
  CANCELLED: 'red',
};

/** 计划状态筛选项 */
const STATUS_FILTER = Object.entries(STATUS_LABEL).map(([value, label]) => ({ value, label }));

/** 时间展示 */
function formatTime(value?: string): string {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-';
}

/** 生成排程表单值 */
interface GenerateFormValues {
  planDate?: Dayjs;
  days?: number;
  plannedBy?: string;
}

/** 有限产能排程页 */
export default function SchedulingPage() {
  const [plans, setPlans] = useState<MesSchedulePlan[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [planDate, setPlanDate] = useState<Dayjs | undefined>(undefined);
  const [workOrderCode, setWorkOrderCode] = useState('');
  const [status, setStatus] = useState<MesScheduleStatus | undefined>(undefined);
  const [generateOpen, setGenerateOpen] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [detail, setDetail] = useState<MesSchedulePlan | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [form] = Form.useForm<GenerateFormValues>();

  const canManage = hasPermission('sys:mes:scheduling:manage');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageMesSchedulePlans({
        planDate: planDate ? planDate.format('YYYY-MM-DD') : undefined,
        workOrderCode: workOrderCode || undefined,
        status,
        current,
        size,
      });
      setPlans(page.records);
      setTotal(page.total);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [planDate, workOrderCode, status, current, size]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleGenerate = async () => {
    const values = await form.validateFields();
    setGenerating(true);
    try {
      const result = await generateMesSchedule({
        planDate: values.planDate ? values.planDate.format('YYYY-MM-DD') : undefined,
        days: values.days,
        plannedBy: values.plannedBy || undefined,
      });
      message.success(
        `已生成 ${result.planCount} 个计划 / ${result.slotCount} 个时段${
          result.unscheduledTasks > 0 ? `，${result.unscheduledTasks} 个任务容量不足` : ''
        }`,
      );
      setGenerateOpen(false);
      form.resetFields();
      setCurrent(1);
      await load();
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setGenerating(false);
    }
  };

  const handleLock = async (plan: MesSchedulePlan) => {
    try {
      await lockMesSchedulePlan(plan.id);
      message.success('排程已锁定');
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const handleCancel = async (plan: MesSchedulePlan) => {
    try {
      await cancelMesSchedulePlan(plan.id);
      message.success('排程已取消，时段已释放');
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const handleDetail = async (plan: MesSchedulePlan) => {
    setDetailLoading(true);
    try {
      setDetail(await getMesSchedulePlan(plan.id));
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setDetailLoading(false);
    }
  };

  const columns: TableProps<MesSchedulePlan>['columns'] = [
    {
      title: '计划号',
      dataIndex: 'planNo',
      width: 170,
      render: (value: string, record) => (
        <Button type="link" size="small" style={{ padding: 0 }} onClick={() => void handleDetail(record)}>
          {value}
        </Button>
      ),
    },
    { title: '工单编码', dataIndex: 'workOrderCode', width: 150 },
    { title: '排程日期', dataIndex: 'planDate', width: 120 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: MesScheduleStatus) => <Tag color={STATUS_COLOR[value]}>{STATUS_LABEL[value]}</Tag>,
    },
    { title: '排程人', dataIndex: 'plannedBy', width: 120, render: (value?: string) => value || '-' },
    { title: '创建时间', dataIndex: 'createTime', width: 170, render: (value?: string) => formatTime(value) },
    { title: '备注', dataIndex: 'remark', render: (value?: string) => value || '-' },
    {
      title: '操作',
      key: 'action',
      width: 170,
      render: (_, record) =>
        record.status === 'DRAFT' && canManage ? (
          <Space size="small">
            <Popconfirm title="锁定后作为执行依据，不可再取消？" onConfirm={() => void handleLock(record)}>
              <Button type="link" size="small">
                锁定
              </Button>
            </Popconfirm>
            <Popconfirm title="取消排程并释放全部时段？" onConfirm={() => void handleCancel(record)}>
              <Button type="link" size="small" danger>
                取消
              </Button>
            </Popconfirm>
          </Space>
        ) : (
          <Button type="link" size="small" onClick={() => void handleDetail(record)}>
            详情
          </Button>
        ),
    },
  ];

  const slotColumns: TableProps<MesScheduleSlot>['columns'] = [
    { title: '排程日期', dataIndex: 'scheduleDate', width: 120 },
    { title: '工作中心', dataIndex: 'workCenterCode', width: 120 },
    { title: '任务编号', dataIndex: 'taskNo', width: 150, render: (value?: string) => value || '-' },
    {
      title: '工序',
      dataIndex: 'operationName',
      render: (value: string | undefined, record) => value || record.operationCode || '-',
    },
    { title: '班次产能', dataIndex: 'capacityQuantity', width: 110 },
    { title: '分配数量', dataIndex: 'allocatedQuantity', width: 110 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: string) => (value === 'RESERVED' ? <Tag color="blue">已预留</Tag> : <Tag>已释放</Tag>),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <DatePicker
          placeholder="排程日期"
          value={planDate}
          onChange={(value) => {
            setPlanDate(value ?? undefined);
            setCurrent(1);
          }}
        />
        <Input
          placeholder="工单编码"
          allowClear
          style={{ width: 180 }}
          value={workOrderCode}
          onChange={(event) => {
            setWorkOrderCode(event.target.value);
            setCurrent(1);
          }}
        />
        <Select
          placeholder="状态"
          allowClear
          style={{ width: 140 }}
          options={STATUS_FILTER}
          value={status}
          onChange={(value) => {
            setStatus(value);
            setCurrent(1);
          }}
        />
        <Button onClick={() => void load()}>查询</Button>
        {canManage && (
          <Button type="primary" onClick={() => setGenerateOpen(true)}>
            生成排程
          </Button>
        )}
      </Space>
      <Table
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={plans}
        pagination={{
          current,
          pageSize: size,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (page, pageSize) => {
            setCurrent(page);
            setSize(pageSize);
          },
        }}
      />

      <Modal
        title="生成有限产能排程"
        open={generateOpen}
        confirmLoading={generating}
        onOk={() => void handleGenerate()}
        onCancel={() => setGenerateOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical" initialValues={{ days: 7 }}>
          <Form.Item name="planDate" label="排程起始日期">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="days" label="排程天数" rules={[{ required: true, message: '请填写排程天数' }]}>
            <InputNumber min={1} max={30} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="plannedBy" label="排程人">
            <Input maxLength={64} placeholder="可选" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={detail ? `排程详情 ${detail.planNo}` : '排程详情'}
        width={760}
        open={detail !== null}
        onClose={() => setDetail(null)}
      >
        <Spin spinning={detailLoading}>
          {detail && (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Descriptions size="small" column={2} bordered>
              <Descriptions.Item label="工单编码">{detail.workOrderCode || '-'}</Descriptions.Item>
              <Descriptions.Item label="排程日期">{detail.planDate}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={STATUS_COLOR[detail.status]}>{STATUS_LABEL[detail.status]}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="排程人">{detail.plannedBy || '-'}</Descriptions.Item>
              <Descriptions.Item label="创建时间" span={2}>
                {formatTime(detail.createTime)}
              </Descriptions.Item>
              <Descriptions.Item label="备注" span={2}>
                {detail.remark || '-'}
              </Descriptions.Item>
            </Descriptions>
            <Table
              rowKey="id"
              size="small"
              columns={slotColumns}
              dataSource={detail.slots ?? []}
              pagination={false}
            />
          </Space>
        )}
        </Spin>
      </Drawer>
    </Space>
  );
}
