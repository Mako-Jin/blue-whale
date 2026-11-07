// OA 考勤日报页：筛选查询 + 按日期区间重算
import { useCallback, useEffect, useState } from 'react';
import { App, Button, DatePicker, Form, InputNumber, Modal, Select, Space, Table, Tag } from 'antd';
import type { TableProps } from 'antd';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import {
  pageDaily,
  recalculateDaily,
  type OaAttendanceDaily,
  type OaAttendanceDailyStatus,
} from '../../api/attendance';
import { hasPermission } from '../../utils/permission';

/** 日报状态文案 */
const STATUS_META: Record<OaAttendanceDailyStatus, { text: string; color: string }> = {
  NORMAL: { text: '正常', color: 'success' },
  LATE: { text: '迟到', color: 'warning' },
  EARLY: { text: '早退', color: 'warning' },
  LATE_AND_EARLY: { text: '迟到早退', color: 'error' },
  ABSENT: { text: '缺勤', color: 'error' },
  LEAVE: { text: '请假', color: 'blue' },
  OFFICIAL: { text: '公出', color: 'purple' },
};

/** 时间字段去除日期前缀 */
function formatTime(value?: string): string {
  if (!value) {
    return '-';
  }
  const parts = value.split('T');
  return parts.length > 1 ? parts[1].slice(0, 5) : value;
}

export default function AttendanceDailyPage() {
  const { message } = App.useApp();
  const [data, setData] = useState<OaAttendanceDaily[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [userId, setUserId] = useState<number | undefined>();
  const [status, setStatus] = useState<OaAttendanceDailyStatus | ''>('');
  const [dateRange, setDateRange] = useState<[Dayjs, Dayjs] | null>(null);
  const [recalcOpen, setRecalcOpen] = useState(false);
  const [recalculating, setRecalculating] = useState(false);

  const [form] = Form.useForm<{ dateRange: [Dayjs, Dayjs]; userId?: number }>();

  const canRecalculate = hasPermission('sys:oa:attendance:daily:recalculate');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageDaily({
        current,
        size,
        userId,
        status,
        dateFrom: dateRange?.[0].format('YYYY-MM-DD'),
        dateTo: dateRange?.[1].format('YYYY-MM-DD'),
      });
      setData(page.records);
      setTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '考勤日报查询失败');
      setData([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [current, size, userId, status, dateRange, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const openRecalculate = () => {
    form.resetFields();
    form.setFieldsValue({ dateRange: [dayjs().startOf('month'), dayjs()] });
    setRecalcOpen(true);
  };

  const handleRecalculate = async () => {
    const values = await form.validateFields();
    setRecalculating(true);
    try {
      const count = await recalculateDaily({
        dateFrom: values.dateRange[0].format('YYYY-MM-DD'),
        dateTo: values.dateRange[1].format('YYYY-MM-DD'),
        userId: values.userId,
      });
      message.success(`已重算 ${count} 条考勤日报`);
      setRecalcOpen(false);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '考勤日报重算失败');
    } finally {
      setRecalculating(false);
    }
  };

  const columns: TableProps<OaAttendanceDaily>['columns'] = [
    { title: '日期', dataIndex: 'workDate', key: 'workDate', width: 120 },
    { title: '用户ID', dataIndex: 'userId', key: 'userId', width: 100 },
    { title: '姓名', dataIndex: 'userName', key: 'userName', width: 110 },
    { title: '班次', dataIndex: 'shiftCode', key: 'shiftCode', width: 110 },
    {
      title: '班次时间',
      key: 'shift',
      width: 130,
      render: (_value, record) => `${record.shiftStart ?? '-'} ~ ${record.shiftEnd ?? '-'}`,
    },
    {
      title: '首次上班',
      dataIndex: 'firstIn',
      key: 'firstIn',
      width: 110,
      render: (value: string) => formatTime(value),
    },
    {
      title: '末次下班',
      dataIndex: 'lastOut',
      key: 'lastOut',
      width: 110,
      render: (value: string) => formatTime(value),
    },
    { title: '迟到(分)', dataIndex: 'lateMinutes', key: 'lateMinutes', width: 90 },
    { title: '早退(分)', dataIndex: 'earlyMinutes', key: 'earlyMinutes', width: 90 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (value: OaAttendanceDailyStatus) => {
        const meta = STATUS_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    { title: '备注', dataIndex: 'remark', key: 'remark', ellipsis: true },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <InputNumber
          min={1}
          precision={0}
          placeholder="用户ID"
          style={{ width: 120 }}
          value={userId}
          onChange={(value) => {
            setUserId(value ?? undefined);
            setCurrent(1);
          }}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 140 }}
          value={status || undefined}
          options={Object.entries(STATUS_META).map(([value, meta]) => ({ label: meta.text, value }))}
          onChange={(value) => {
            setStatus((value as OaAttendanceDailyStatus) ?? '');
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
        {canRecalculate ? (
          <Button type="primary" onClick={openRecalculate}>
            重算日报
          </Button>
        ) : null}
        <Button onClick={() => void load()}>刷新</Button>
      </Space>
      <Table<OaAttendanceDaily>
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
        open={recalcOpen}
        title="重算考勤日报"
        onOk={() => void handleRecalculate()}
        confirmLoading={recalculating}
        onCancel={() => setRecalcOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="dateRange"
            label="重算日期区间"
            rules={[{ required: true, message: '请选择重算日期区间' }]}
          >
            <DatePicker.RangePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="userId" label="指定用户ID">
            <InputNumber style={{ width: '100%' }} min={1} precision={0} placeholder="留空重算全部成员" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
