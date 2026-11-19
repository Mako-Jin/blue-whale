// 执行统计页签：概览指标 + 按天趋势 + 失败/耗时/异常类型 TopN。
// 不引入图表库，复用 Statistic / Table / Progress，与运营统计页保持一致的呈现方式。

import {
  Alert,
  App,
  Button,
  Card,
  DatePicker,
  Input,
  InputNumber,
  Progress,
  Space,
  Statistic,
  Table,
  Tag,
  Tooltip,
  Typography,
  type TableProps,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useEffect, useState } from 'react';
import {
  getJobLogStats,
  type JobDurationTopVO,
  type JobErrorTypeTopVO,
  type JobFailureTopVO,
  type JobLogDailyStatVO,
  type JobLogStatsParams,
  type JobLogStatsVO,
} from '../../api/job';

const { RangePicker } = DatePicker;

type RangeValue = [Dayjs | null, Dayjs | null] | null;

/** 默认统计窗口：含今天在内的最近 7 天 */
function defaultRange(): [Dayjs, Dayjs] {
  return [dayjs().subtract(6, 'day').startOf('day'), dayjs().endOf('day')];
}

function toNumber(value: string | undefined): number {
  const parsed = Number(value ?? 0);
  return Number.isFinite(parsed) ? parsed : 0;
}

/** 毫秒耗时转可读文本：1 秒以上按秒展示 */
function formatDuration(value: string | undefined): string {
  if (value === undefined) {
    return '-';
  }
  const millis = toNumber(value);
  return millis >= 1000 ? `${(millis / 1000).toFixed(2)} s` : `${millis} ms`;
}

/** 按天统计的成功率：只统计该天已结束的执行 */
function dailySuccessRate(row: JobLogDailyStatVO): number {
  const total = toNumber(row.successCount) + toNumber(row.failureCount);
  return total === 0 ? 0 : (toNumber(row.successCount) / total) * 100;
}

const dailyColumns: TableProps<JobLogDailyStatVO>['columns'] = [
  { title: '日期', dataIndex: 'date', width: 120 },
  { title: '成功', dataIndex: 'successCount', width: 90 },
  {
    title: '失败',
    dataIndex: 'failureCount',
    width: 90,
    render: (value: string | undefined) => (
      <Typography.Text type={toNumber(value) > 0 ? 'danger' : undefined}>{value ?? '0'}</Typography.Text>
    ),
  },
  {
    title: '成功率',
    key: 'successRate',
    width: 200,
    render: (_, row) => (
      <Progress percent={Number(dailySuccessRate(row).toFixed(1))} size="small" />
    ),
  },
  {
    title: '平均耗时',
    dataIndex: 'avgDurationMillis',
    width: 120,
    render: (value: string | undefined) => formatDuration(value),
  },
  {
    title: 'P95 耗时',
    dataIndex: 'p95DurationMillis',
    width: 120,
    render: (value: string | undefined) => formatDuration(value),
  },
];

const failureTopColumns: TableProps<JobFailureTopVO>['columns'] = [
  {
    title: '任务',
    dataIndex: 'jobName',
    render: (value: string | undefined, row) =>
      value ? `${value}（#${row.jobId}）` : `#${row.jobId}`,
  },
  { title: '失败次数', dataIndex: 'failureCount', width: 100 },
];

const durationTopColumns: TableProps<JobDurationTopVO>['columns'] = [
  {
    title: '任务',
    dataIndex: 'jobName',
    render: (value: string | undefined, row) =>
      value ? `${value}（#${row.jobId}）` : `#${row.jobId}`,
  },
  {
    title: '平均耗时',
    dataIndex: 'avgDurationMillis',
    width: 110,
    render: (value: string | undefined) => formatDuration(value),
  },
  { title: '执行次数', dataIndex: 'executionCount', width: 100 },
];

const errorTypeTopColumns: TableProps<JobErrorTypeTopVO>['columns'] = [
  {
    title: '异常类型',
    dataIndex: 'errorType',
    width: 200,
    render: (value: string | undefined) => <Tag color="error">{value ?? 'OTHER'}</Tag>,
  },
  { title: '次数', dataIndex: 'count', width: 90 },
  {
    title: '示例信息',
    dataIndex: 'sampleMessage',
    ellipsis: true,
    render: (value: string | undefined) =>
      value ? (
        <Tooltip title={value}>
          <Typography.Text ellipsis style={{ maxWidth: 320 }}>
            {value}
          </Typography.Text>
        </Tooltip>
      ) : (
        '-'
      ),
  },
];

export function JobStatsPanel() {
  const { message } = App.useApp();
  const [jobId, setJobId] = useState<number | null>(null);
  const [jobGroup, setJobGroup] = useState('');
  const [range, setRange] = useState<RangeValue>(defaultRange);
  const [applied, setApplied] = useState<JobLogStatsParams>(() => ({
    from: defaultRange()[0]?.format('YYYY-MM-DDTHH:mm:ss'),
    to: defaultRange()[1]?.format('YYYY-MM-DDTHH:mm:ss'),
  }));
  const [stats, setStats] = useState<JobLogStatsVO | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    getJobLogStats(applied)
      .then((result) => {
        if (!cancelled) {
          setStats(result);
        }
      })
      .catch((error) => {
        if (!cancelled) {
          message.error(error instanceof Error ? error.message : '执行统计加载失败');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [applied, message]);

  const handleSearch = () => {
    setApplied({
      jobId: jobId === null ? undefined : String(jobId),
      jobGroup: jobGroup.trim() || undefined,
      from: range?.[0]?.format('YYYY-MM-DDTHH:mm:ss'),
      to: range?.[1]?.format('YYYY-MM-DDTHH:mm:ss'),
    });
  };

  const handleReset = () => {
    const nextRange = defaultRange();
    setJobId(null);
    setJobGroup('');
    setRange(nextRange);
    setApplied({
      from: nextRange[0]?.format('YYYY-MM-DDTHH:mm:ss'),
      to: nextRange[1]?.format('YYYY-MM-DDTHH:mm:ss'),
    });
  };

  const daily = stats?.daily ?? [];
  const total = toNumber(stats?.totalCount);
  const successTotal = daily.reduce((sum, row) => sum + toNumber(row.successCount), 0);
  const failureTotal = daily.reduce((sum, row) => sum + toNumber(row.failureCount), 0);
  const successRate = total === 0 ? 0 : (successTotal / total) * 100;
  // 日均耗时按每天参与统计的执行数加权，避免小样本日被同等对待
  const averageMillis =
    total === 0
      ? 0
      : daily.reduce(
          (sum, row) =>
            sum +
            toNumber(row.avgDurationMillis) *
              (toNumber(row.successCount) + toNumber(row.failureCount)),
          0,
        ) / total;
  const topNEmpty =
    applied.jobId !== undefined &&
    (stats?.failureTop.length ?? 0) === 0 &&
    (stats?.durationTop.length ?? 0) === 0 &&
    (stats?.errorTypeTop.length ?? 0) === 0;

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card>
        <Space wrap>
          <InputNumber
            min={1}
            placeholder="任务ID"
            value={jobId}
            onChange={(value) => setJobId(value ?? null)}
            style={{ width: 140 }}
          />
          <Input
            allowClear
            placeholder="任务分组"
            value={jobGroup}
            onChange={(event) => setJobGroup(event.target.value)}
            style={{ width: 180 }}
          />
          <RangePicker
            value={range}
            onChange={(dates) => setRange(dates as RangeValue)}
            allowClear={false}
          />
          <Button type="primary" onClick={handleSearch}>
            查询
          </Button>
          <Button onClick={handleReset}>重置</Button>
        </Space>
        <div style={{ marginTop: 12 }}>
          <Typography.Text type="secondary">
            统计窗口：{stats?.from ?? '-'} ~ {stats?.to ?? '-'}
          </Typography.Text>
        </div>
      </Card>

      {stats?.sampled && (
        <Alert
          type="warning"
          showIcon
          message="样本已截断"
          description="窗口内明细超过 10 万条，均值与 P95 为近似值。"
        />
      )}

      <Card title="执行概览" loading={loading}>
        <Space size={48} wrap>
          <Statistic title="执行总数" value={total} />
          <Statistic title="成功" value={successTotal} valueStyle={{ color: '#3f8600' }} />
          <Statistic
            title="失败"
            value={failureTotal}
            valueStyle={failureTotal > 0 ? { color: '#cf1322' } : undefined}
          />
          <Statistic
            title="成功率"
            value={total === 0 ? '-' : `${successRate.toFixed(1)}%`}
          />
          <Statistic title="加权平均耗时" value={formatDuration(String(averageMillis))} />
        </Space>
      </Card>

      <Card title="按天趋势" loading={loading}>
        <Table<JobLogDailyStatVO>
          rowKey="date"
          size="small"
          columns={dailyColumns}
          dataSource={daily}
          pagination={false}
        />
      </Card>

      {topNEmpty && (
        <Typography.Text type="secondary">指定任务时 TopN 不适用，可查看该任务的按天趋势。</Typography.Text>
      )}

      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Card title="失败次数 Top10" loading={loading}>
          <Table<JobFailureTopVO>
            rowKey="jobId"
            size="small"
            columns={failureTopColumns}
            dataSource={stats?.failureTop ?? []}
            pagination={false}
          />
        </Card>
        <Card title="平均耗时 Top10" loading={loading}>
          <Table<JobDurationTopVO>
            rowKey="jobId"
            size="small"
            columns={durationTopColumns}
            dataSource={stats?.durationTop ?? []}
            pagination={false}
          />
        </Card>
        <Card title="失败原因 Top10" loading={loading}>
          <Table<JobErrorTypeTopVO>
            rowKey="errorType"
            size="small"
            columns={errorTypeTopColumns}
            dataSource={stats?.errorTypeTop ?? []}
            pagination={false}
          />
        </Card>
      </Space>
    </Space>
  );
}
