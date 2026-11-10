// ERP 产能负荷页：日负荷超载识别 + 工序排程甘特（纯 CSS，无新增依赖）
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, DatePicker, Select, Space, Table, Tabs, Tag } from 'antd';
import type { TableProps } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  getCrpLoad,
  listAllWorkCenters,
  type CrpLoadView,
  type CrpScheduleView,
  type CrpView,
  type WorkCenterView,
} from '../api/mfg';
import { quantity } from '../utils/scm';

/** 产能负荷页 */
export default function CrpPage() {
  const [from, setFrom] = useState<Dayjs>(dayjs());
  const [to, setTo] = useState<Dayjs>(dayjs().add(29, 'day'));
  const [code, setCode] = useState<string | undefined>();
  const [centers, setCenters] = useState<WorkCenterView[]>([]);
  const [result, setResult] = useState<CrpView | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setResult(
        await getCrpLoad({
          fromDate: from.format('YYYY-MM-DD'),
          toDate: to.format('YYYY-MM-DD'),
          workCenterCode: code,
        }),
      );
    } finally {
      setLoading(false);
    }
  }, [from, to, code]);

  useEffect(() => {
    listAllWorkCenters().then(setCenters).catch(() => setCenters([]));
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const totalDays = Math.max(to.diff(from, 'day') + 1, 1);
  const overloadDays = useMemo(
    () => (result?.loads ?? []).filter((row) => row.overload).length,
    [result],
  );
  const delayedOperations = useMemo(
    () => (result?.schedule ?? []).filter((row) => row.delayed).length,
    [result],
  );

  const loadColumns: TableProps<CrpLoadView>['columns'] = [
    { title: '工作中心', dataIndex: 'workCenterCode', key: 'workCenterCode', width: 140 },
    { title: '日期', dataIndex: 'date', key: 'date', width: 120 },
    {
      title: '产能(小时)',
      dataIndex: 'capacityHours',
      key: 'capacityHours',
      width: 110,
      render: quantity,
    },
    {
      title: '负荷(小时)',
      dataIndex: 'loadHours',
      key: 'loadHours',
      width: 110,
      render: quantity,
    },
    {
      title: '利用率',
      dataIndex: 'utilizationPercent',
      key: 'utilizationPercent',
      width: 100,
      render: (value: number) => `${quantity(value)}%`,
    },
    {
      title: '状态',
      dataIndex: 'overload',
      key: 'overload',
      width: 90,
      render: (value: boolean) => (
        <Tag color={value ? 'red' : 'green'}>{value ? '超载' : '正常'}</Tag>
      ),
    },
  ];

  const scheduleColumns: TableProps<CrpScheduleView>['columns'] = [
    { title: '生产订单', dataIndex: 'orderNo', key: 'orderNo', width: 180 },
    { title: '物料', dataIndex: 'itemCode', key: 'itemCode', width: 120 },
    {
      title: '工序',
      key: 'step',
      width: 160,
      render: (_, record) => `${record.stepNo} ${record.name}`,
    },
    { title: '工作中心', dataIndex: 'workCenterCode', key: 'workCenterCode', width: 120 },
    {
      title: '数量/工时',
      key: 'quantity',
      width: 120,
      render: (_, record) => `${quantity(record.quantity)} / ${quantity(record.hours)}h`,
    },
    {
      title: `${from.format('MM-DD')} ~ ${to.format('MM-DD')}`,
      key: 'gantt',
      render: (_, record) => {
        const start = dayjs(record.startDate);
        const end = dayjs(record.endDate);
        const offset = Math.max(start.diff(from, 'day'), 0);
        const span = Math.max(end.diff(start, 'day') + 1, 1);
        const left = (offset / totalDays) * 100;
        const width = Math.min((span / totalDays) * 100, 100 - left);
        return (
          <div style={{ position: 'relative', height: 20, minWidth: 260, background: '#f5f5f5' }}>
            <div
              title={`${record.startDate} ~ ${record.endDate}`}
              style={{
                position: 'absolute',
                left: `${left}%`,
                width: `${width}%`,
                height: '100%',
                background: record.delayed ? '#ff7875' : '#52c41a',
                borderRadius: 2,
              }}
            />
          </div>
        );
      },
    },
    {
      title: '状态',
      key: 'delayed',
      width: 90,
      render: (_, record) => (
        <Tag color={record.delayed ? 'red' : 'green'}>{record.delayed ? '延期' : '按期'}</Tag>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <DatePicker
          value={from}
          onChange={(value) => value && setFrom(value)}
          allowClear={false}
          placeholder="开始日期"
        />
        <DatePicker
          value={to}
          onChange={(value) => value && setTo(value)}
          allowClear={false}
          placeholder="结束日期"
        />
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder="全部工作中心"
          style={{ width: 200 }}
          value={code}
          onChange={setCode}
          options={centers.map((center) => ({
            value: center.code,
            label: `${center.code} ${center.name}`,
          }))}
        />
        <Button type="primary" onClick={() => void load()} loading={loading}>
          查询
        </Button>
        {overloadDays > 0 ? <Tag color="red">超载 {overloadDays} 天次</Tag> : null}
        {delayedOperations > 0 ? <Tag color="orange">延期工序 {delayedOperations} 道</Tag> : null}
        {result ? <Tag>排除订单 {result.excludedOrderCount}</Tag> : null}
        {result && Number(result.beyondHorizonHours) > 0 ? (
          <Tag color="orange">区间外工时 {quantity(result.beyondHorizonHours)}h</Tag>
        ) : null}
      </Space>
      <Tabs
        items={[
          {
            key: 'load',
            label: '产能负荷',
            children: (
              <Table<CrpLoadView>
                rowKey={(record) => `${record.workCenterCode}-${record.date}`}
                loading={loading}
                size="small"
                columns={loadColumns}
                dataSource={result?.loads ?? []}
                pagination={{ pageSize: 20 }}
              />
            ),
          },
          {
            key: 'schedule',
            label: '排程甘特',
            children: (
              <Table<CrpScheduleView>
                rowKey={(record) => `${record.orderNo}-${record.stepNo}`}
                loading={loading}
                size="small"
                columns={scheduleColumns}
                dataSource={result?.schedule ?? []}
                pagination={{ pageSize: 20 }}
                scroll={{ x: 1100 }}
              />
            ),
          },
        ]}
      />
    </Space>
  );
}
