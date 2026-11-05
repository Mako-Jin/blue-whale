// OA 工作日历页：日期区间筛选、日历表格与按日期幂等保存弹窗
import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Space,
  Switch,
  Table,
  Tag,
} from 'antd';
import type { TableProps } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  pageWorkCalendars,
  saveWorkCalendar,
  type WorkCalendarSaveCommand,
  type WorkCalendarView,
} from '../../api/hr';
import { hasPermission } from '../../utils/permission';

/** 表单字段 */
interface FormValues {
  calendarDate: Dayjs;
  workday: boolean;
  workHours?: number;
  holidayName?: string;
  remark?: string;
}

/** 查询条件 */
interface Query {
  current: number;
  size: number;
  startDate?: string;
  endDate?: string;
}

export default function CalendarCenter() {
  const { message } = App.useApp();
  const [form] = Form.useForm<FormValues>();
  const [data, setData] = useState<WorkCalendarView[]>([]);
  const [total, setTotal] = useState(0);
  const [query, setQuery] = useState<Query>({
    current: 1,
    size: 10,
    startDate: dayjs().startOf('month').format('YYYY-MM-DD'),
    endDate: dayjs().endOf('month').format('YYYY-MM-DD'),
  });
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<WorkCalendarView | null>(null);

  const canEdit = hasPermission('sys:oa:hr:calendar:edit');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageWorkCalendars({
        current: query.current,
        size: query.size,
        startDate: query.startDate,
        endDate: query.endDate,
      });
      setData(page.records);
      setTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '工作日历查询失败');
      setData([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [query, message]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!modalOpen) {
      return;
    }
    if (editing) {
      form.setFieldsValue({
        calendarDate: dayjs(editing.calendarDate),
        workday: editing.workday,
        workHours: editing.workHours,
        holidayName: editing.holidayName,
        remark: editing.remark,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({ calendarDate: dayjs(), workday: true, workHours: 8 });
    }
  }, [modalOpen, editing, form]);

  const openCreate = () => {
    setEditing(null);
    setModalOpen(true);
  };

  const openEdit = (record: WorkCalendarView) => {
    setEditing(record);
    setModalOpen(true);
  };

  const handleOk = async () => {
    const values = await form.validateFields();
    const payload: WorkCalendarSaveCommand = {
      workday: values.workday,
      workHours: values.workHours ?? undefined,
      holidayName: values.holidayName?.trim() || undefined,
      remark: values.remark?.trim() || undefined,
    };
    try {
      await saveWorkCalendar(values.calendarDate.format('YYYY-MM-DD'), payload);
      message.success('保存成功');
      setModalOpen(false);
      setEditing(null);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    }
  };

  const columns: TableProps<WorkCalendarView>['columns'] = [
    { title: '日期', dataIndex: 'calendarDate', key: 'calendarDate', width: 140 },
    {
      title: '工作日',
      dataIndex: 'workday',
      key: 'workday',
      width: 100,
      render: (value: boolean) => (
        <Tag color={value ? 'green' : 'default'}>{value ? '是' : '否'}</Tag>
      ),
    },
    {
      title: '工时',
      dataIndex: 'workHours',
      key: 'workHours',
      width: 100,
      render: (value?: number) => (value ?? 8),
    },
    {
      title: '节假日名称',
      dataIndex: 'holidayName',
      key: 'holidayName',
      width: 160,
      render: (value?: string) => value || '-',
    },
    {
      title: '备注',
      dataIndex: 'remark',
      key: 'remark',
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_value, record) =>
        canEdit ? (
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <DatePicker
          placeholder="开始日期"
          value={query.startDate ? dayjs(query.startDate) : undefined}
          onChange={(value) =>
            setQuery((prev) => ({
              ...prev,
              current: 1,
              startDate: value ? value.format('YYYY-MM-DD') : undefined,
            }))
          }
        />
        <DatePicker
          placeholder="结束日期"
          value={query.endDate ? dayjs(query.endDate) : undefined}
          onChange={(value) =>
            setQuery((prev) => ({
              ...prev,
              current: 1,
              endDate: value ? value.format('YYYY-MM-DD') : undefined,
            }))
          }
        />
        {canEdit ? (
          <Button type="primary" onClick={openCreate}>
            配置工作日历
          </Button>
        ) : null}
        <Button onClick={() => void load()}>刷新</Button>
      </Space>
      <Table<WorkCalendarView>
        rowKey="calendarDate"
        loading={loading}
        columns={columns}
        dataSource={data}
        pagination={{
          current: query.current,
          pageSize: query.size,
          total,
          showSizeChanger: true,
          onChange: (page, pageSize) =>
            setQuery((prev) => ({ ...prev, current: page, size: pageSize })),
        }}
      />
      <Modal
        open={modalOpen}
        title={editing ? '编辑工作日历' : '配置工作日历'}
        onOk={() => void handleOk()}
        onCancel={() => {
          setModalOpen(false);
          setEditing(null);
        }}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="calendarDate"
            label="日期"
            rules={[{ required: true, message: '请选择日期' }]}
          >
            <DatePicker style={{ width: '100%' }} disabled={Boolean(editing)} />
          </Form.Item>
          <Form.Item name="workday" label="工作日" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="workHours" label="当日标准工时">
            <InputNumber style={{ width: '100%' }} min={0} step={0.5} precision={1} />
          </Form.Item>
          <Form.Item name="holidayName" label="节假日名称">
            <Input placeholder="如 春节" maxLength={64} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} showCount placeholder="选填" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
