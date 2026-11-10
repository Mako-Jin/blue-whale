// OA 打卡记录页：筛选查询 + 上班/下班打卡
import { useCallback, useEffect, useState } from 'react';
import { App, Button, DatePicker, InputNumber, Select, Space, Table, Tag } from 'antd';
import type { TableProps } from 'antd';
import type { Dayjs } from 'dayjs';
import { getCurrentUser } from '../../api/client';
import {
  pageRecords,
  punch,
  type OaAttendancePunchType,
  type OaAttendanceRecord,
  type OaAttendanceSource,
} from '../../api/attendance';
import { hasPermission } from '../../utils/permission';

/** 打卡类型文案 */
const PUNCH_META: Record<OaAttendancePunchType, { text: string; color: string }> = {
  IN: { text: '上班', color: 'blue' },
  OUT: { text: '下班', color: 'green' },
};

/** 来源文案 */
const SOURCE_META: Record<OaAttendanceSource, string> = {
  APP: '移动端',
  DEVICE: '考勤机',
  WEB: '网页',
};

export default function AttendancePunchPage() {
  const { message } = App.useApp();
  const user = getCurrentUser();
  const [data, setData] = useState<OaAttendanceRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [userId, setUserId] = useState<number | undefined>(
    typeof user?.userId === 'number' ? user.userId : undefined,
  );
  const [punchType, setPunchType] = useState<OaAttendancePunchType | ''>('');
  const [dateRange, setDateRange] = useState<[Dayjs, Dayjs] | null>(null);
  const [punching, setPunching] = useState(false);

  const canPunch = hasPermission('sys:oa:attendance:record:add');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageRecords({
        current,
        size,
        userId,
        punchType,
        dateFrom: dateRange?.[0].format('YYYY-MM-DD'),
        dateTo: dateRange?.[1].format('YYYY-MM-DD'),
      });
      setData(page.records);
      setTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '打卡记录查询失败');
      setData([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [current, size, userId, punchType, dateRange, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const handlePunch = async (type: OaAttendancePunchType) => {
    setPunching(true);
    try {
      await punch({ punchType: type, source: 'WEB', userName: user?.nickname ?? user?.username });
      message.success(type === 'IN' ? '上班打卡成功' : '下班打卡成功');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '打卡失败');
    } finally {
      setPunching(false);
    }
  };

  const columns: TableProps<OaAttendanceRecord>['columns'] = [
    { title: '用户ID', dataIndex: 'userId', key: 'userId', width: 100 },
    { title: '姓名', dataIndex: 'userName', key: 'userName', width: 110 },
    {
      title: '打卡时间',
      dataIndex: 'punchTime',
      key: 'punchTime',
      width: 180,
      render: (value: string) => value?.replace('T', ' ') ?? '-',
    },
    {
      title: '类型',
      dataIndex: 'punchType',
      key: 'punchType',
      width: 90,
      render: (value: OaAttendancePunchType) => {
        const meta = PUNCH_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '来源',
      dataIndex: 'source',
      key: 'source',
      width: 90,
      render: (value: OaAttendanceSource) => SOURCE_META[value] ?? value ?? '-',
    },
    { title: '地址', dataIndex: 'address', key: 'address', ellipsis: true },
    { title: '设备编码', dataIndex: 'deviceCode', key: 'deviceCode', width: 130 },
    { title: '备注', dataIndex: 'remark', key: 'remark', width: 150, ellipsis: true },
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
          placeholder="打卡类型"
          style={{ width: 120 }}
          value={punchType || undefined}
          options={Object.entries(PUNCH_META).map(([value, meta]) => ({ label: meta.text, value }))}
          onChange={(value) => {
            setPunchType((value as OaAttendancePunchType) ?? '');
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
        {canPunch ? (
          <>
            <Button type="primary" loading={punching} onClick={() => void handlePunch('IN')}>
              上班打卡
            </Button>
            <Button loading={punching} onClick={() => void handlePunch('OUT')}>
              下班打卡
            </Button>
          </>
        ) : null}
        <Button onClick={() => void load()}>刷新</Button>
      </Space>
      <Table<OaAttendanceRecord>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        scroll={{ x: 1200 }}
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
    </Space>
  );
}
