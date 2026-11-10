// OA 出差申请页：筛选查询、新增/编辑弹窗（含行程明细）、提交报告、撤销与删除
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
} from 'antd';
import type { TableProps } from 'antd';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import {
  createTrip,
  deleteTrip,
  getTrip,
  pageTrips,
  submitTripReport,
  updateTrip,
  updateTripStatus,
  type OaTrip,
  type OaTripInput,
  type OaTripReportStatus,
  type OaTripStatus,
  type OaTripTransport,
} from '../../api/trip';
import { hasPermission } from '../../utils/permission';

/** 状态文案与颜色 */
const STATUS_META: Record<OaTripStatus, { text: string; color: string }> = {
  PENDING: { text: '待审批', color: 'processing' },
  APPROVED: { text: '已通过', color: 'success' },
  REJECTED: { text: '已驳回', color: 'error' },
  CANCELLED: { text: '已撤销', color: 'default' },
};

/** 交通方式文案 */
const TRANSPORT_META: Record<OaTripTransport, string> = {
  TRAIN: '火车',
  PLANE: '飞机',
  CAR: '用车',
  OTHER: '其他',
};

/** 报告状态文案 */
const REPORT_META: Record<OaTripReportStatus, { text: string; color: string }> = {
  PENDING: { text: '待提交', color: 'warning' },
  SUBMITTED: { text: '已提交', color: 'success' },
};

/** 行程表单行 */
interface ItineraryFormValues {
  tripDate?: Dayjs;
  fromPlace?: string;
  toPlace?: string;
  transport?: OaTripTransport;
  remark?: string;
}

/** 表单字段 */
interface TripFormValues {
  code: string;
  applicant: string;
  applicantId?: number;
  destination: string;
  dateRange: [Dayjs, Dayjs];
  transportType: OaTripTransport;
  budgetAmount?: number;
  advanceAmount?: number;
  itineraries?: ItineraryFormValues[];
  remark?: string;
}

export default function TripPage() {
  const { message } = App.useApp();
  const [data, setData] = useState<OaTrip[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [code, setCode] = useState('');
  const [status, setStatus] = useState<OaTripStatus | ''>('');
  const [applicant, setApplicant] = useState('');
  const [destination, setDestination] = useState('');
  const [dateRange, setDateRange] = useState<[Dayjs, Dayjs] | null>(null);

  const [editing, setEditing] = useState<OaTrip | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [reporting, setReporting] = useState<OaTrip | null>(null);
  const [reportOpen, setReportOpen] = useState(false);
  const [reportSaving, setReportSaving] = useState(false);

  const [form] = Form.useForm<TripFormValues>();
  const [reportForm] = Form.useForm<{ content: string }>();

  const canAdd = hasPermission('sys:oa:trip:add');
  const canEdit = hasPermission('sys:oa:trip:edit');
  const canDelete = hasPermission('sys:oa:trip:delete');
  const canReport = hasPermission('sys:oa:trip:report');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageTrips({
        current,
        size,
        code: code.trim() || undefined,
        status,
        applicant: applicant.trim() || undefined,
        destination: destination.trim() || undefined,
        dateFrom: dateRange?.[0].format('YYYY-MM-DD'),
        dateTo: dateRange?.[1].format('YYYY-MM-DD'),
      });
      setData(page.records);
      setTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '出差申请查询失败');
      setData([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [current, size, code, status, applicant, destination, dateRange, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({
      transportType: 'TRAIN',
      dateRange: [dayjs(), dayjs().add(1, 'day')],
      budgetAmount: 0,
      advanceAmount: 0,
      itineraries: [],
    });
    setModalOpen(true);
  };

  const openEdit = async (record: OaTrip) => {
    setEditing(record);
    form.resetFields();
    const detail = await getTrip(record.id).catch(() => record);
    form.setFieldsValue({
      code: detail.code,
      applicant: detail.applicant,
      applicantId: detail.applicantId,
      destination: detail.destination,
      dateRange: [dayjs(detail.startDate), dayjs(detail.endDate)],
      transportType: detail.transportType,
      budgetAmount: detail.budgetAmount,
      advanceAmount: detail.advanceAmount,
      itineraries: (detail.itineraries ?? []).map((item) => ({
        tripDate: dayjs(item.tripDate),
        fromPlace: item.fromPlace,
        toPlace: item.toPlace,
        transport: item.transport,
        remark: item.remark,
      })),
      remark: detail.remark,
    });
    setModalOpen(true);
  };

  const buildItineraries = (values: ItineraryFormValues[] | undefined, start: Dayjs, end: Dayjs) =>
    (values ?? []).map((item, index) => ({
      dayNo: index + 1,
      tripDate: (item.tripDate ?? start).format('YYYY-MM-DD'),
      fromPlace: item.fromPlace?.trim() ?? '',
      toPlace: item.toPlace?.trim() ?? '',
      transport: item.transport ?? 'TRAIN',
      remark: item.remark?.trim() || undefined,
    })).filter((item) => {
      const inRange = !dayjs(item.tripDate).isBefore(start, 'day') && !dayjs(item.tripDate).isAfter(end, 'day');
      return item.fromPlace !== '' && item.toPlace !== '' && inRange;
    });

  const handleSave = async () => {
    const values = await form.validateFields();
    const [start, end] = values.dateRange;
    const payload: OaTripInput = {
      code: values.code.trim(),
      applicant: values.applicant.trim(),
      applicantId: values.applicantId,
      destination: values.destination.trim(),
      startDate: start.format('YYYY-MM-DD'),
      endDate: end.format('YYYY-MM-DD'),
      transportType: values.transportType,
      budgetAmount: values.budgetAmount ?? 0,
      advanceAmount: values.advanceAmount ?? 0,
      itineraries: buildItineraries(values.itineraries, start, end),
      remark: values.remark?.trim() || undefined,
    };
    setSaving(true);
    try {
      if (editing) {
        await updateTrip(editing.id, payload);
        message.success('出差申请已更新');
      } else {
        await createTrip(payload);
        message.success('出差申请已提交');
      }
      setModalOpen(false);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '出差申请保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleCancel = async (record: OaTrip) => {
    try {
      await updateTripStatus(record.id, 'CANCELLED');
      message.success('出差申请已撤销');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '撤销失败');
    }
  };

  const handleDelete = async (record: OaTrip) => {
    try {
      await deleteTrip(record.id);
      message.success('出差申请已删除');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
    }
  };

  const openReport = (record: OaTrip) => {
    setReporting(record);
    reportForm.resetFields();
    setReportOpen(true);
  };

  const handleReport = async () => {
    if (!reporting) {
      return;
    }
    const values = await reportForm.validateFields();
    setReportSaving(true);
    try {
      await submitTripReport(reporting.id, values.content.trim());
      message.success('出差报告已提交');
      setReportOpen(false);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '出差报告提交失败');
    } finally {
      setReportSaving(false);
    }
  };

  const columns: TableProps<OaTrip>['columns'] = [
    { title: '申请单号', dataIndex: 'code', key: 'code', width: 170 },
    { title: '申请人', dataIndex: 'applicant', key: 'applicant', width: 110 },
    { title: '目的地', dataIndex: 'destination', key: 'destination', width: 130, ellipsis: true },
    {
      title: '出差日期',
      key: 'period',
      width: 200,
      render: (_value, record) => `${record.startDate} ~ ${record.endDate}`,
    },
    { title: '天数', dataIndex: 'days', key: 'days', width: 70 },
    {
      title: '交通',
      dataIndex: 'transportType',
      key: 'transportType',
      width: 80,
      render: (value: OaTripTransport) => TRANSPORT_META[value] ?? value,
    },
    {
      title: '预算/预支(元)',
      key: 'amount',
      width: 140,
      render: (_value, record) => `${record.budgetAmount ?? 0} / ${record.advanceAmount ?? 0}`,
    },
    {
      title: '报告',
      dataIndex: 'reportStatus',
      key: 'reportStatus',
      width: 90,
      render: (value: OaTripReportStatus) => {
        const meta = REPORT_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (value: OaTripStatus) => {
        const meta = STATUS_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_value, record) => {
        const actions = [];
        if (record.status === 'PENDING' && canEdit) {
          actions.push(
            <Button key="edit" type="link" size="small" onClick={() => void openEdit(record)}>
              编辑
            </Button>,
            <Button key="cancel" type="link" size="small" onClick={() => void handleCancel(record)}>
              撤销
            </Button>,
          );
        }
        if (record.status === 'PENDING' && canDelete) {
          actions.push(
            <Popconfirm key="delete" title="确认删除该出差申请？" onConfirm={() => void handleDelete(record)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>,
          );
        }
        if (record.status === 'APPROVED' && record.reportStatus === 'PENDING' && canReport) {
          actions.push(
            <Button key="report" type="link" size="small" onClick={() => openReport(record)}>
              提交报告
            </Button>,
          );
        }
        return actions.length > 0 ? <Space size={4}>{actions}</Space> : '-';
      },
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input
          allowClear
          placeholder="申请单号"
          style={{ width: 170 }}
          value={code}
          onChange={(event) => {
            setCode(event.target.value);
            setCurrent(1);
          }}
        />
        <Input
          allowClear
          placeholder="申请人账号"
          style={{ width: 150 }}
          value={applicant}
          onChange={(event) => {
            setApplicant(event.target.value);
            setCurrent(1);
          }}
        />
        <Input
          allowClear
          placeholder="目的地"
          style={{ width: 150 }}
          value={destination}
          onChange={(event) => {
            setDestination(event.target.value);
            setCurrent(1);
          }}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 130 }}
          value={status || undefined}
          options={Object.entries(STATUS_META).map(([value, meta]) => ({
            label: meta.text,
            value,
          }))}
          onChange={(value) => {
            setStatus((value as OaTripStatus) ?? '');
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
            新增出差
          </Button>
        ) : null}
        <Button onClick={() => void load()}>刷新</Button>
      </Space>
      <Table<OaTrip>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        scroll={{ x: 1300 }}
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
        title={editing ? '编辑出差申请' : '新增出差申请'}
        width={760}
        onOk={() => void handleSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              name="code"
              label="申请单号"
              rules={[{ required: true, message: '请输入申请单号' }]}
              style={{ flex: 1 }}
            >
              <Input maxLength={64} placeholder="租户内唯一" />
            </Form.Item>
            <Form.Item
              name="applicant"
              label="申请人账号"
              rules={[{ required: true, message: '请输入申请人账号' }]}
              style={{ flex: 1 }}
            >
              <Input maxLength={64} />
            </Form.Item>
            <Form.Item name="applicantId" label="申请人用户ID" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} min={1} precision={0} placeholder="选填" />
            </Form.Item>
          </Space>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              name="destination"
              label="出差目的地"
              rules={[{ required: true, message: '请输入出差目的地' }]}
              style={{ flex: 1 }}
            >
              <Input maxLength={128} />
            </Form.Item>
            <Form.Item
              name="dateRange"
              label="出差日期"
              rules={[{ required: true, message: '请选择出差起止日期' }]}
              style={{ flex: 1 }}
            >
              <DatePicker.RangePicker style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="transportType" label="交通方式" style={{ flex: 1 }}>
              <Select
                options={Object.entries(TRANSPORT_META).map(([value, label]) => ({ label, value }))}
              />
            </Form.Item>
          </Space>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="budgetAmount" label="预算金额(元)" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} min={0} precision={2} />
            </Form.Item>
            <Form.Item name="advanceAmount" label="预支金额(元)" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} min={0} precision={2} />
            </Form.Item>
          </Space>
          <Form.List name="itineraries">
            {(fields, { add, remove }) => (
              <Space direction="vertical" size="small" style={{ width: '100%' }}>
                <Space>
                  <span>出差行程</span>
                  <Button size="small" onClick={() => add({ transport: 'TRAIN' })}>
                    添加行程
                  </Button>
                </Space>
                {fields.map((field) => (
                  <Space key={field.key} size="small" align="baseline" wrap>
                    <Form.Item
                      name={[field.name, 'tripDate']}
                      rules={[{ required: true, message: '选择日期' }]}
                    >
                      <DatePicker placeholder="行程日期" />
                    </Form.Item>
                    <Form.Item
                      name={[field.name, 'fromPlace']}
                      rules={[{ required: true, message: '出发地' }]}
                    >
                      <Input style={{ width: 130 }} placeholder="出发地" maxLength={128} />
                    </Form.Item>
                    <Form.Item
                      name={[field.name, 'toPlace']}
                      rules={[{ required: true, message: '到达地' }]}
                    >
                      <Input style={{ width: 130 }} placeholder="到达地" maxLength={128} />
                    </Form.Item>
                    <Form.Item name={[field.name, 'transport']}>
                      <Select
                        style={{ width: 100 }}
                        options={Object.entries(TRANSPORT_META).map(([value, label]) => ({
                          label,
                          value,
                        }))}
                      />
                    </Form.Item>
                    <Form.Item name={[field.name, 'remark']}>
                      <Input style={{ width: 150 }} placeholder="备注" maxLength={255} />
                    </Form.Item>
                    <Button type="link" size="small" danger onClick={() => remove(field.name)}>
                      移除
                    </Button>
                  </Space>
                ))}
              </Space>
            )}
          </Form.List>
          <Form.Item name="remark" label="备注" style={{ marginTop: 12 }}>
            <Input.TextArea rows={2} maxLength={255} showCount placeholder="选填" />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={reportOpen}
        title="提交出差报告"
        onOk={() => void handleReport()}
        confirmLoading={reportSaving}
        onCancel={() => setReportOpen(false)}
        destroyOnClose
      >
        <Form form={reportForm} layout="vertical">
          <Form.Item
            name="content"
            label="报告内容"
            rules={[{ required: true, message: '请输入出差报告内容' }]}
          >
            <Input.TextArea rows={6} maxLength={2048} showCount placeholder="出差成果、问题与后续计划" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
