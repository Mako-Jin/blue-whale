// 学习看板（M6）：覆盖率 / 完成率 / 通过率 / 学分分布 + 趋势 + 历史快照 + 导出。
//
// 权限码与 migration-5.87-lms-m6.sql 对齐：
//   sys:lms:analytics:view（查看）、sys:lms:analytics:refresh（刷新快照）、
//   sys:lms:analytics:export（导出）。
// 口径：完成率的分母是「学习人数」而不是「在职人数」——完成率回答的是
// 「已经开始学的人里多少学完了」，若把没开始的人塞进分母，它就和覆盖率重复了。

import { Alert, Button, Card, Col, DatePicker, Progress, Row, Space, Statistic, Table, Tag, message } from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import { hasPermission } from '../api/client';
import {
  exportCsv,
  getOverview,
  pageSnapshots,
  refreshSnapshot,
  type AnalyticsOverview,
  type MetricSnapshotItem,
} from '../api/analytics';

/** 比率展示（保留一位小数） */
function rate(value?: number): string {
  return `${(value ?? 0).toFixed(1)}%`;
}

/** 单个比率进度条 */
function RateBar({ label, value, hint }: { label: string; value: number; hint: string }) {
  return (
    <div style={{ marginBottom: 12 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
        <span>{label}</span>
        <span style={{ color: '#8c8c8c' }}>{hint}</span>
      </div>
      <Progress percent={Number(value.toFixed(1))} status="active" />
    </div>
  );
}

/** 学习看板页 */
export default function AnalyticsPage() {
  const [day, setDay] = useState<Dayjs>(dayjs());
  const [data, setData] = useState<AnalyticsOverview | null>(null);
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [history, setHistory] = useState<MetricSnapshotItem[]>([]);
  const [historyTotal, setHistoryTotal] = useState(0);
  const [historyPage, setHistoryPage] = useState(1);
  const [historyLoading, setHistoryLoading] = useState(false);

  const canRefresh = hasPermission('sys:lms:analytics:refresh');
  const canExport = hasPermission('sys:lms:analytics:export');

  const load = useCallback(async (target: Dayjs) => {
    setLoading(true);
    try {
      setData(await getOverview(target.format('YYYY-MM-DD')));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '看板加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  const loadHistory = useCallback(async (page: number) => {
    setHistoryLoading(true);
    try {
      const result = await pageSnapshots({ current: page, size: 10 });
      setHistory(result.records);
      setHistoryTotal(result.total);
      setHistoryPage(page);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '历史快照加载失败');
    } finally {
      setHistoryLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(day);
  }, [day, load]);

  useEffect(() => {
    void loadHistory(1);
  }, [loadHistory]);

  const handleRefresh = async () => {
    setRefreshing(true);
    try {
      const result = await refreshSnapshot(day.format('YYYY-MM-DD'));
      message.success(`已刷新 ${result.statDate} 快照（${result.created ? '新建' : '覆盖'}）`);
      await load(day);
      await loadHistory(1);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '刷新失败');
    } finally {
      setRefreshing(false);
    }
  };

  const handleExport = async () => {
    setExporting(true);
    try {
      await exportCsv(30);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '导出失败');
    } finally {
      setExporting(false);
    }
  };

  const historyColumns = [
    { title: '统计日', dataIndex: 'statDate', width: 110 },
    { title: '范围', dataIndex: 'scopeName', width: 90 },
    { title: '在职', dataIndex: 'memberTotal', width: 70 },
    { title: '学习人数', dataIndex: 'learnerTotal', width: 90 },
    { title: '覆盖率', dataIndex: 'coverageRate', width: 90, render: (v: number) => rate(v) },
    { title: '完成人数', dataIndex: 'learnerCompleted', width: 90 },
    { title: '完成率', dataIndex: 'completionRate', width: 90, render: (v: number) => rate(v) },
    { title: '参考/通过', width: 110, render: (_: unknown, row: MetricSnapshotItem) => `${row.examTakerTotal}/${row.examPassedTotal}` },
    { title: '通过率', dataIndex: 'passRate', width: 90, render: (v: number) => rate(v) },
    { title: '学分总量', dataIndex: 'creditTotal', width: 100 },
    { title: '获分人数', dataIndex: 'creditEarners', width: 90 },
    {
      title: '计算时间',
      dataIndex: 'computedAt',
      width: 170,
      render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-'),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        title="学习看板"
        extra={
          <Space>
            <DatePicker
              value={day}
              allowClear={false}
              onChange={(value) => value && setDay(value)}
              disabledDate={(current) => current.isAfter(dayjs(), 'day')}
            />
            {canRefresh && (
              <Button type="primary" loading={refreshing} onClick={handleRefresh}>
                刷新快照
              </Button>
            )}
            {canExport && (
              <Button loading={exporting} onClick={handleExport}>
                导出近 30 天
              </Button>
            )}
          </Space>
        }
        loading={loading}
      >
        {data?.live && (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message="当天还没有快照，当前展示为实时计算结果（未落库）"
            description="点「刷新快照」才会把这一天的数字固化下来，之后历史趋势就按快照走。"
          />
        )}
        <Row gutter={[16, 16]}>
          <Col xs={12} md={6}>
            <Statistic title="在职人数" value={data?.memberTotal ?? 0} suffix="人" />
          </Col>
          <Col xs={12} md={6}>
            <Statistic title="学习人数" value={data?.learnerTotal ?? 0} suffix="人" />
          </Col>
          <Col xs={12} md={6}>
            <Statistic title="完成人数" value={data?.learnerCompleted ?? 0} suffix="人" />
          </Col>
          <Col xs={12} md={6}>
            <Statistic title="学分总量" value={data?.creditTotal ?? 0} precision={1} />
          </Col>
        </Row>
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={24} md={12}>
          <Card title="学习情况">
            <RateBar
              label="学习覆盖率"
              value={data?.coverageRate ?? 0}
              hint={`${data?.learnerTotal ?? 0} / ${data?.memberTotal ?? 0} 在职人数`}
            />
            <RateBar
              label="学习完成率"
              value={data?.completionRate ?? 0}
              hint={`${data?.learnerCompleted ?? 0} / ${data?.learnerTotal ?? 0} 学习人数`}
            />
            <RateBar
              label="考试通过率"
              value={data?.passRate ?? 0}
              hint={`${data?.examPassedTotal ?? 0} / ${data?.examTakerTotal ?? 0} 参考人数`}
            />
            <div style={{ color: '#8c8c8c', fontSize: 12 }}>
              完成率与通过率的分母都是「已经参与的人」，两者都按人去重，补考通过不会重复计数。
            </div>
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card title="学分分布" extra={<Tag>按人合计</Tag>}>
            {(data?.creditDistribution ?? []).length === 0 ? (
              <div style={{ color: '#8c8c8c' }}>暂无学分记录</div>
            ) : (
              (data?.creditDistribution ?? []).map((bucket) => {
                const max = Math.max(...(data?.creditDistribution ?? []).map((item) => item.earners), 1);
                return (
                  <div key={bucket.label} style={{ marginBottom: 12 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                      <span>{bucket.label} 分</span>
                      <span style={{ color: '#8c8c8c' }}>{bucket.earners} 人</span>
                    </div>
                    <Progress
                      percent={Number(((bucket.earners / max) * 100).toFixed(1))}
                      showInfo={false}
                      strokeColor="#1677ff"
                    />
                  </div>
                );
              })
            )}
            <div style={{ color: '#8c8c8c', fontSize: 12 }}>
              按人的学分合计分档：一个人学完三门各 2 分属于「5-10」档，按笔统计会把同一人拆成三笔。
            </div>
          </Card>
        </Col>
      </Row>

      <Card title="历史快照" extra={<span style={{ color: '#8c8c8c' }}>共 {historyTotal} 条</span>}>
        <Table
          rowKey="statDate"
          size="small"
          loading={historyLoading}
          columns={historyColumns}
          dataSource={history}
          pagination={{
            current: historyPage,
            pageSize: 10,
            total: historyTotal,
            onChange: (page) => void loadHistory(page),
            showSizeChanger: false,
          }}
        />
      </Card>
    </Space>
  );
}
