// 党务数据看板（M-P4）：党员结构 / 党费收缴 / 组织生活 / 转正与换届提醒。
//
// 看板只读快照或实时兜底，不在这里改业务数据：
// 「刷新快照」是写归档值，「导出」只带聚合数，三个权限拆开控权。
// live=true 时必须显式提示「当日尚未生成快照」，避免把实时值当审计值。

import { Alert, Button, Card, DatePicker, Space, Table, Tag, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import {
  exportPartyMetric,
  getPartyOverview,
  refreshPartyMetric,
  type MetricItemView,
  type MetricOverviewView,
  type MetricTrendPointView,
} from '../api/party';
import { hasPermission } from '../api/client';

/** 指标列 */
const METRIC_COLUMNS: ColumnsType<MetricItemView> = [
  { title: '指标', dataIndex: 'label', key: 'label' },
  { title: '分子', dataIndex: 'numerator', key: 'numerator', align: 'right' },
  { title: '分母', dataIndex: 'denominator', key: 'denominator', align: 'right' },
  {
    title: '比率',
    dataIndex: 'rate',
    key: 'rate',
    align: 'right',
    render: (rate: number, row) => (row.ratio ? `${rate}%` : '—'),
  },
];

/** 趋势列 */
const TREND_COLUMNS: ColumnsType<MetricTrendPointView> = [
  { title: '统计日', dataIndex: 'statDate', key: 'statDate' },
  { title: '分子', dataIndex: 'numerator', key: 'numerator', align: 'right' },
  { title: '分母', dataIndex: 'denominator', key: 'denominator', align: 'right' },
  {
    title: '收缴率',
    dataIndex: 'rate',
    key: 'rate',
    align: 'right',
    render: (rate: number) => `${rate}%`,
  },
];

/** 党务数据看板页 */
export default function DashboardPage() {
  const [date, setDate] = useState<Dayjs>(dayjs());
  const [overview, setOverview] = useState<MetricOverviewView | null>(null);
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  const canManage = hasPermission('sys:party:metric:manage');
  const canExport = hasPermission('sys:party:metric:export');

  const load = useCallback(async (statDate: Dayjs) => {
    setLoading(true);
    try {
      setOverview(await getPartyOverview(statDate.format('YYYY-MM-DD')));
    } catch (err) {
      message.error(err instanceof Error ? err.message : '看板加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(date);
  }, [date, load]);

  const handleRefresh = async () => {
    setRefreshing(true);
    try {
      await refreshPartyMetric(date.format('YYYY-MM-DD'));
      message.success('当日快照已覆盖刷新');
      await load(date);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '刷新失败');
    } finally {
      setRefreshing(false);
    }
  };

  const handleExport = async () => {
    try {
      const text = date.format('YYYY-MM-DD');
      await exportPartyMetric(text, text);
      message.success('导出已开始（仅聚合数，不含个人信息）');
    } catch (err) {
      message.error(err instanceof Error ? err.message : '导出失败');
    }
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card size="small">
        <Space wrap>
          <DatePicker
            allowClear={false}
            value={date}
            onChange={(value) => value && setDate(value)}
            placeholder="统计日"
          />
          <Tag color={overview?.scopeType === 'BRANCH' ? 'blue' : 'geekblue'}>
            {overview?.scopeName ?? '全租户'}
          </Tag>
          {canManage ? (
            <Button loading={refreshing} onClick={() => void handleRefresh()}>
              刷新快照
            </Button>
          ) : null}
          {canExport ? <Button onClick={() => void handleExport()}>导出 CSV</Button> : null}
        </Space>
      </Card>

      {overview?.live ? (
        <Alert
          type="warning"
          showIcon
          message="当日尚未生成快照"
          description="当前展示的是实时计算结果，归档前请先刷新快照，避免把实时值当作审计值。"
        />
      ) : null}

      <Card title="核心指标" size="small">
        <Table
          rowKey="metricType"
          size="small"
          loading={loading}
          columns={METRIC_COLUMNS}
          dataSource={overview?.items ?? []}
          pagination={false}
        />
      </Card>

      <Card title="近 30 日党费收缴趋势" size="small">
        <Table
          rowKey="statDate"
          size="small"
          columns={TREND_COLUMNS}
          dataSource={overview?.trend ?? []}
          pagination={false}
          locale={{ emptyText: '暂无趋势快照' }}
        />
      </Card>
    </Space>
  );
}
