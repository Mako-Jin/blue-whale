// 工会数据看板（M-P4）：会员覆盖率 / 活动参与率 / 提案办结率。
//
// 覆盖率的分子来自工会会员台账、分母来自 ERP 员工快照；分母不能拿会员数冒名，
// 所以这里提供「同步员工目录」入口，同步失败时后端保留上一版快照。

import { Alert, Button, Card, DatePicker, Space, Table, Tag, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import {
  exportUnionMetric,
  getUnionOverview,
  refreshUnionMetric,
  syncEmployeeSnapshot,
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
    title: '覆盖率',
    dataIndex: 'rate',
    key: 'rate',
    align: 'right',
    render: (rate: number) => `${rate}%`,
  },
];

/** 工会数据看板页 */
export default function UnionDashboardPage() {
  const [date, setDate] = useState<Dayjs>(dayjs());
  const [overview, setOverview] = useState<MetricOverviewView | null>(null);
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [syncing, setSyncing] = useState(false);

  const canManage = hasPermission('sys:union:metric:manage');

  const load = useCallback(async (statDate: Dayjs) => {
    setLoading(true);
    try {
      setOverview(await getUnionOverview(statDate.format('YYYY-MM-DD')));
    } catch (err) {
      message.error(err instanceof Error ? err.message : '工会看板加载失败');
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
      await refreshUnionMetric(date.format('YYYY-MM-DD'));
      message.success('工会快照已刷新');
      await load(date);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '刷新失败');
    } finally {
      setRefreshing(false);
    }
  };

  const handleSync = async () => {
    setSyncing(true);
    try {
      const result = await syncEmployeeSnapshot();
      message.success(`员工目录已同步：源 ${result.sourceTotal} 人，在册 ${result.activeTotal} 人`);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '员工目录同步失败');
    } finally {
      setSyncing(false);
    }
  };

  const handleExport = async () => {
    try {
      const text = date.format('YYYY-MM-DD');
      await exportUnionMetric(text, text);
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
          <Tag color="geekblue">{overview?.scopeName ?? '全租户'}</Tag>
          {canManage ? (
            <>
              <Button loading={refreshing} onClick={() => void handleRefresh()}>
                刷新快照
              </Button>
              <Button loading={syncing} onClick={() => void handleSync()}>
                同步员工目录
              </Button>
            </>
          ) : null}
          <Button onClick={() => void handleExport()}>导出 CSV</Button>
        </Space>
      </Card>

      {overview?.live ? (
        <Alert
          type="warning"
          showIcon
          message="当日尚未生成快照"
          description="当前展示的是实时计算结果；归档前请先刷新快照。"
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

      <Card title="近 30 日会员覆盖率趋势" size="small">
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
