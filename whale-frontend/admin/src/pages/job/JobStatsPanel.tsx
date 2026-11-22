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
import { useI18n } from '../../i18n';
import type { MessageKey } from '../../i18n/messages';
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

/** 翻译函数签名（由 useI18n 注入） */
type Translate = (key: MessageKey, vars?: Record<string, string | number>) => string;

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

function buildDailyColumns(t: Translate): TableProps<JobLogDailyStatVO>['columns'] {
  return [
    { title: t('job.stats.col.date'), dataIndex: 'date', width: 120 },
    { title: t('job.stats.col.success'), dataIndex: 'successCount', width: 90 },
    {
      title: t('job.stats.col.failure'),
      dataIndex: 'failureCount',
      width: 90,
      render: (value: string | undefined) => (
        <Typography.Text type={toNumber(value) > 0 ? 'danger' : undefined}>
          {value ?? '0'}
        </Typography.Text>
      ),
    },
    {
      title: t('job.stats.col.successRate'),
      key: 'successRate',
      width: 200,
      render: (_, row) => (
        <Progress percent={Number(dailySuccessRate(row).toFixed(1))} size="small" />
      ),
    },
    {
      title: t('job.stats.col.avgDuration'),
      dataIndex: 'avgDurationMillis',
      width: 120,
      render: (value: string | undefined) => formatDuration(value),
    },
    {
      title: t('job.stats.col.p95'),
      dataIndex: 'p95DurationMillis',
      width: 120,
      render: (value: string | undefined) => formatDuration(value),
    },
  ];
}

function buildFailureTopColumns(t: Translate): TableProps<JobFailureTopVO>['columns'] {
  return [
    {
      title: t('job.stats.col.job'),
      dataIndex: 'jobName',
      render: (value: string | undefined, row) =>
        value ? `${value}（#${row.jobId}）` : `#${row.jobId}`,
    },
    { title: t('job.stats.col.failureCount'), dataIndex: 'failureCount', width: 100 },
  ];
}

function buildDurationTopColumns(t: Translate): TableProps<JobDurationTopVO>['columns'] {
  return [
    {
      title: t('job.stats.col.job'),
      dataIndex: 'jobName',
      render: (value: string | undefined, row) =>
        value ? `${value}（#${row.jobId}）` : `#${row.jobId}`,
    },
    {
      title: t('job.stats.col.avgDuration'),
      dataIndex: 'avgDurationMillis',
      width: 110,
      render: (value: string | undefined) => formatDuration(value),
    },
    { title: t('job.stats.col.executionCount'), dataIndex: 'executionCount', width: 100 },
  ];
}

function buildErrorTypeTopColumns(t: Translate): TableProps<JobErrorTypeTopVO>['columns'] {
  return [
    {
      title: t('job.stats.col.errorType'),
      dataIndex: 'errorType',
      width: 200,
      render: (value: string | undefined) => <Tag color="error">{value ?? 'OTHER'}</Tag>,
    },
    { title: t('job.stats.col.count'), dataIndex: 'count', width: 90 },
    {
      title: t('job.stats.col.sample'),
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
}

export function JobStatsPanel() {
  const { message } = App.useApp();
  const { t } = useI18n();
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
          message.error(error instanceof Error ? error.message : t('job.stats.msg.loadFailed'));
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
  }, [applied, message, t]);

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
  const dailyColumns = buildDailyColumns(t);
  const failureTopColumns = buildFailureTopColumns(t);
  const durationTopColumns = buildDurationTopColumns(t);
  const errorTypeTopColumns = buildErrorTypeTopColumns(t);
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
            placeholder={t('job.stats.filter.jobId')}
            value={jobId}
            onChange={(value) => setJobId(value ?? null)}
            style={{ width: 140 }}
          />
          <Input
            allowClear
            placeholder={t('job.stats.filter.jobGroup')}
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
            {t('job.stats.action.search')}
          </Button>
          <Button onClick={handleReset}>{t('job.stats.action.reset')}</Button>
        </Space>
        <div style={{ marginTop: 12 }}>
          <Typography.Text type="secondary">
            {t('job.stats.window', { from: stats?.from ?? '-', to: stats?.to ?? '-' })}
          </Typography.Text>
        </div>
      </Card>

      {stats?.sampled && (
        <Alert
          type="warning"
          showIcon
          message={t('job.stats.sampled.title')}
          description={t('job.stats.sampled.desc')}
        />
      )}

      <Card title={t('job.stats.overview.title')} loading={loading}>
        <Space size={48} wrap>
          <Statistic title={t('job.stats.overview.total')} value={total} />
          <Statistic
            title={t('job.stats.overview.success')}
            value={successTotal}
            valueStyle={{ color: '#3f8600' }}
          />
          <Statistic
            title={t('job.stats.overview.failure')}
            value={failureTotal}
            valueStyle={failureTotal > 0 ? { color: '#cf1322' } : undefined}
          />
          <Statistic
            title={t('job.stats.overview.successRate')}
            value={total === 0 ? '-' : `${successRate.toFixed(1)}%`}
          />
          <Statistic
            title={t('job.stats.overview.avgDuration')}
            value={formatDuration(String(averageMillis))}
          />
        </Space>
      </Card>

      <Card title={t('job.stats.daily.title')} loading={loading}>
        <Table<JobLogDailyStatVO>
          rowKey="date"
          size="small"
          columns={dailyColumns}
          dataSource={daily}
          pagination={false}
        />
      </Card>

      {topNEmpty && (
        <Typography.Text type="secondary">{t('job.stats.topNUnavailable')}</Typography.Text>
      )}

      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Card title={t('job.stats.failureTop.title')} loading={loading}>
          <Table<JobFailureTopVO>
            rowKey="jobId"
            size="small"
            columns={failureTopColumns}
            dataSource={stats?.failureTop ?? []}
            pagination={false}
          />
        </Card>
        <Card title={t('job.stats.durationTop.title')} loading={loading}>
          <Table<JobDurationTopVO>
            rowKey="jobId"
            size="small"
            columns={durationTopColumns}
            dataSource={stats?.durationTop ?? []}
            pagination={false}
          />
        </Card>
        <Card title={t('job.stats.errorTypeTop.title')} loading={loading}>
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
