// OA 加班申请页：筛选查询、新增/编辑弹窗、撤销与删除
import { useCallback, useEffect, useState } from 'react';
import {
  App,
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
  TimePicker,
} from 'antd';
import type { TableProps } from 'antd';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import {
  createOvertime,
  deleteOvertime,
  pageOvertimes,
  updateOvertime,
  updateOvertimeStatus,
  type OaOvertime,
  type OaOvertimeCompensation,
  type OaOvertimeInput,
  type OaOvertimeStatus,
} from '../../api/overtime';
import { hasPermission } from '../../utils/permission';

/** 状态文案与颜色 */
const STATUS_META: Record<OaOvertimeStatus, { text: string; color: string }> = {
  PENDING: { text: '待审批', color: 'processing' },
  APPROVED: { text: '已通过', color: 'success' },
  REJECTED: { text: '已驳回', color: 'error' },
  CANCELLED: { text: '已撤销', color: 'default' },
};

/** 补偿方式文案 */
const COMPENSATION_META: Record<OaOvertimeCompensation, string> = {
  COMP_TIME: '调休',
  PAY: '加班费',
};

/** 表单字段 */
interface OvertimeFormValues {
  code: string;
  applicant: string;
  applicantId?: number;
  overtimeDate: Dayjs;
  timeRange: [Dayjs, Dayjs];
  compensation: OaOvertimeCompensation;
  projectName?: string;
  reason?: string;
  remark?: string;
}

/** 时段格式化为 HH:mm */
function formatTime(value?: string): string {
  return value ? value.slice(0, 5) : '-';
}

export default function OvertimePage() {
  const { message } = App.useApp();
  const [data, setData] = useState<OaOvertime[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [code, setCode] = useState('');
  const [status, setStatus] = useState<OaOvertimeStatus | ''>('');
  const [applicant, setApplicant] = useState('');
  const [dateRange, setDateRange] = useState<[Dayjs, Dayjs] | null>(null);

  const [editing, setEditing] = useState<OaOvertime | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<OvertimeFormValues>();

  const canAdd = hasPermission('sys:oa:overtime:add');
  const canEdit = hasPermission('sys:oa:overtime:edit');
  const canDelete = hasPermission('sys:oa:overtime:delete');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageOvertimes({
        current,
        size,
        code: code.trim() || undefined,
        status,
        applicant: applicant.trim() || undefined,
        dateFrom: dateRange?.[0].format('YYYY-MM-DD'),
        dateTo: dateRange?.[1].format('YYYY-MM-DD'),
      });
      setData(page.records);
      setTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加班申请查询失败');
      setData([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [current, size, code, status, applicant, dateRange, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({
      compensation: 'COMP_TIME',
      overtimeDate: dayjs(),
      timeRange: [dayjs().hour(18).minute(0), dayjs().hour(21).minute(0)],
    });
    setModalOpen(true);
  };

  const openEdit = (record: OaOvertime) => {
    setEditing(record);
    form.resetFields();
    form.setFieldsValue({
      code: record.code,
      applicant: record.applicant,
      applicantId: record.applicantId,
      overtimeDate: dayjs(record.overtimeDate),
      timeRange: [
        dayjs(`2000-01-01 ${record.startTime}`),
        dayjs(`2000-01-01 ${record.endTime}`),
      ],
      compensation: record.compensation,
      projectName: record.projectName,
      reason: record.reason,
      remark: record.remark,
    });
    setModalOpen(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    const payload: OaOvertimeInput = {
      code: values.code.trim(),
      applicant: values.applicant.trim(),
      applicantId: values.applicantId,
      overtimeDate: values.overtimeDate.format('YYYY-MM-DD'),
      startTime: values.timeRange[0].format('HH:mm:ss'),
      endTime: values.timeRange[1].format('HH:mm:ss'),
      compensation: values.compensation,
      projectName: values.projectName?.trim() || undefined,
      reason: values.reason?.trim() || undefined,
      remark: values.remark?.trim() || undefined,
    };
    setSaving(true);
    try {
      if (editing) {
        await updateOvertime(editing.id, payload);
        message.success('加班申请已更新');
      } else {
        await createOvertime(payload);
        message.success('加班申请已提交');
      }
      setModalOpen(false);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加班申请保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleCancel = async (record: OaOvertime) => {
    try {
      await updateOvertimeStatus(record.id, 'CANCELLED');
      message.success('加班申请已撤销');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '撤销失败');
    }
  };

  const handleDelete = async (record: OaOvertime) => {
    try {
      await deleteOvertime(record.id);
      message.success('加班申请已删除');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
    }
  };

  const columns: TableProps<OaOvertime>['columns'] = [
    { title: '申请单号', dataIndex: 'code', key: 'code', width: 170 },
    { title: '申请人', dataIndex: 'applicant', key: 'applicant', width: 120 },
    { title: '加班日期', dataIndex: 'overtimeDate', key: 'overtimeDate', width: 120 },
    {
      title: '时段',
      key: 'period',
      width: 130,
      render: (_value, record) => `${formatTime(record.startTime)}-${formatTime(record.endTime)}`,
    },
    {
      title: '时长(小时)',
      dataIndex: 'hours',
      key: 'hours',
      width: 110,
      render: (value: number) => value ?? '-',
    },
    {
      title: '补偿方式',
      dataIndex: 'compensation',
      key: 'compensation',
      width: 100,
      render: (value: OaOvertimeCompensation) => COMPENSATION_META[value] ?? value,
    },
    { title: '项目', dataIndex: 'projectName', key: 'projectName', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: OaOvertimeStatus) => {
        const meta = STATUS_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 180,
      render: (_value, record) => {
        if (record.status !== 'PENDING') {
          return '-';
        }
        return (
          <Space size={4}>
            {canEdit ? (
              <>
                <Button type="link" size="small" onClick={() => openEdit(record)}>
                  编辑
                </Button>
                <Button type="link" size="small" onClick={() => void handleCancel(record)}>
                  撤销
                </Button>
              </>
            ) : null}
            {canDelete ? (
              <Popconfirm title="确认删除该加班申请？" onConfirm={() => void handleDelete(record)}>
                <Button type="link" size="small" danger>
                  删除
                </Button>
              </Popconfirm>
            ) : null}
          </Space>
        );
      },
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input
          allowClear
          placeholder="申请单号"
          style={{ width: 180 }}
          value={code}
          onChange={(event) => {
            setCode(event.target.value);
            setCurrent(1);
          }}
        />
        <Input
          allowClear
          placeholder="申请人账号"
          style={{ width: 160 }}
          value={applicant}
          onChange={(event) => {
            setApplicant(event.target.value);
            setCurrent(1);
          }}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 140 }}
          value={status || undefined}
          options={Object.entries(STATUS_META).map(([value, meta]) => ({
            label: meta.text,
            value,
          }))}
          onChange={(value) => {
            setStatus((value as OaOvertimeStatus) ?? '');
            setCurrent(1);
          }}
        />
        <DatePicker.RangePicker
          value={dateRange}
          onChange={(value) => {
            setDateRange(value as [Dayjs, Dayjs] | null);
            setCurrent(1);
          }}
        />
        {canAdd ? (
          <Button type="primary" onClick={openCreate}>
            新增加班
          </Button>
        ) : null}
        <Button onClick={() => void load()}>刷新</Button>
      </Space>
      <Table<OaOvertime>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        pagination={{
          current,
          pageSize: size,
          total,
          showSizeChanger: true,
          onChange: (page, pageSize) => {
            setCurrent(page);
            setSize(pageSize);
          },
        }}
      />
      <Modal
        open={modalOpen}
        title={editing ? '编辑加班申请' : '新增加班申请'}
        onOk={() => void handleSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="code"
            label="申请单号"
            rules={[{ required: true, message: '请输入申请单号' }]}
          >
            <Input maxLength={64} placeholder="租户内唯一" />
          </Form.Item>
          <Form.Item
            name="applicant"
            label="申请人账号"
            rules={[{ required: true, message: '请输入申请人账号' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="applicantId" label="申请人用户ID">
            <InputNumber style={{ width: '100%' }} min={1} precision={0} placeholder="选填，用于档案校验与调休入账" />
          </Form.Item>
          <Form.Item
            name="overtimeDate"
            label="加班日期"
            rules={[{ required: true, message: '请选择加班日期' }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="timeRange"
            label="加班时段"
            rules={[{ required: true, message: '请选择加班起止时间' }]}
          >
            <TimePicker.RangePicker
              style={{ width: '100%' }}
              format="HH:mm"
              minuteStep={30}
              order
            />
          </Form.Item>
          <Form.Item name="compensation" label="补偿方式">
            <Select
              options={Object.entries(COMPENSATION_META).map(([value, label]) => ({
                label,
                value,
              }))}
            />
          </Form.Item>
          <Form.Item name="projectName" label="关联项目">
            <Input maxLength={128} placeholder="选填" />
          </Form.Item>
          <Form.Item name="reason" label="加班事由">
            <Input.TextArea rows={2} maxLength={512} showCount placeholder="选填" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} showCount placeholder="选填" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
