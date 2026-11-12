// CRM 数据分析（M6）：销售看板 / 漏斗 / 客户 / 排行 / 回款 / 指标快照 / 导出
//
// 数据来自本地只读投影与每日快照；口径见 api/analytics.ts 顶部注释。
import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Form,
  Input,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import { hasPermission } from '../../utils/permission';
import {
  createExport,
  downloadExport,
  generateSnapshot,
  getCollectionAnalysis,
  getCustomerAnalysis,
  getDashboard,
  getFunnel,
  getRanking,
  pageSnapshots,
  type AnalyticsExport,
  type CollectionAnalysisData,
  type CustomerAnalysisData,
  type DashboardData,
  type FunnelViewData,
  type MetricSnapshot,
  type RankingRow,
} from '../../api/analytics';

/** 分析 tab */
export type AnalyticsTab =
  | 'dashboard'
  | 'funnel'
  | 'customer'
  | 'ranking'
  | 'collection'
  | 'snapshot'
  | 'export';

/**
 * 读取错误文案。
 *
 * @param error 异常对象
 * @returns 可展示的错误信息
 */
function errorText(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请稍后重试';
}

/**
 * 金额格式化。
 *
 * @param value 金额
 * @returns 展示文本
 */
function money(value?: number | null): string {
  if (value == null) {
    return '-';
  }
  return value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/**
 * 百分比格式化。
 *
 * @param value 百分比数值
 * @returns 展示文本
 */
function percent(value?: number | null): string {
  if (value == null) {
    return '-';
  }
  return `${value}%`;
}

/** 分布表 */
function DistributionTable({ title, data }: { title: string; data: Record<string, number> }) {
  const rows = Object.entries(data ?? {}).map(([key, value]) => ({ key, value }));
  return (
    <Card size="small" title={title}>
      <Table
        size="small"
        rowKey="key"
        dataSource={rows}
        pagination={false}
        columns={[
          { title: '维度', dataIndex: 'key' },
          { title: '数量', dataIndex: 'value', width: 100 },
        ]}
      />
    </Card>
  );
}

/** 看板面板 */
function DashboardPanel() {
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await getDashboard());
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <Card title="销售看板" extra={<Button onClick={() => void load()} loading={loading}>刷新</Button>}>
      {data ? (
        <>
          <Row gutter={[16, 16]}>
            <Col xs={24} sm={12} md={6}><Card><Statistic title="今日新增线索" value={data.todayNewLeads} /></Card></Col>
            <Col xs={24} sm={12} md={6}><Card><Statistic title="本周签单额" value={money(data.weekWonAmount)} /></Card></Col>
            <Col xs={24} sm={12} md={6}>
              <Card><Statistic title="本月目标完成率" value={percent(data.targetCompletionRate)} /></Card>
            </Col>
            <Col xs={24} sm={12} md={6}><Card><Statistic title="预测缺口" value={money(data.forecastGap)} /></Card></Col>
            <Col xs={24} sm={12} md={6}><Card><Statistic title="本月赢单额" value={money(data.monthWonAmount)} /></Card></Col>
            <Col xs={24} sm={12} md={6}><Card><Statistic title="加权管道金额" value={money(data.weightedPipelineAmount)} /></Card></Col>
            <Col xs={24} sm={12} md={6}><Card><Statistic title="风险商机数" value={data.riskOpportunityCount} /></Card></Col>
            <Col xs={24} sm={12} md={6}><Card><Statistic title="待办行动项" value={data.openActionCount} /></Card></Col>
          </Row>
          <Typography.Text type="secondary" style={{ display: 'block', marginTop: 16 }}>
            统计日：{data.statDate || '今日'}；风险商机指停留超阈值或预计关闭日已过期的未结商机。
          </Typography.Text>
        </>
      ) : (
        <Typography.Text type="secondary">暂无看板数据</Typography.Text>
      )}
    </Card>
  );
}

/** 漏斗面板 */
function FunnelPanel() {
  const [data, setData] = useState<FunnelViewData | null>(null);

  const load = useCallback(async (startDate?: string, endDate?: string) => {
    try {
      setData(await getFunnel(startDate, endDate));
    } catch (error) {
      message.error(errorText(error));
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const columns: TableProps<FunnelViewData['stages'][number]>['columns'] = [
    { title: '阶段', dataIndex: 'stageName', render: (value: string | null | undefined, row) => value || row.stage },
    { title: '数量', dataIndex: 'count', width: 120 },
    { title: '金额', dataIndex: 'amount', width: 160, render: (value?: number) => money(value) },
    { title: '转化率', dataIndex: 'conversionRate', width: 120, render: (value?: number) => percent(value) },
    { title: '平均停留（天）', dataIndex: 'avgStayDays', width: 140, render: (value?: number) => value ?? '-' },
  ];

  return (
    <Card title="销售漏斗分析">
      <Form
        layout="inline"
        style={{ marginBottom: 16 }}
        onFinish={(values) => void load(values.startDate || undefined, values.endDate || undefined)}
      >
        <Form.Item name="startDate"><Input placeholder="开始日期 yyyy-MM-dd" style={{ width: 180 }} /></Form.Item>
        <Form.Item name="endDate"><Input placeholder="结束日期 yyyy-MM-dd" style={{ width: 180 }} /></Form.Item>
        <Form.Item><Button type="primary" htmlType="submit">查询</Button></Form.Item>
      </Form>
      {data && (
        <>
          <Table
            rowKey="stage"
            dataSource={data.stages}
            pagination={false}
            columns={columns}
            summary={() => (
              <Table.Summary.Row>
                <Table.Summary.Cell index={0}>流失</Table.Summary.Cell>
                <Table.Summary.Cell index={1}>{data.lostCount}</Table.Summary.Cell>
                <Table.Summary.Cell index={2}>{money(data.lostAmount)}</Table.Summary.Cell>
                <Table.Summary.Cell index={3} colSpan={2} />
              </Table.Summary.Row>
            )}
          />
          <Typography.Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
            转化率 = 下游阶段数量 ÷ 上游阶段数量；LOST 单列展示，不计入阶段转化。
          </Typography.Text>
        </>
      )}
    </Card>
  );
}

/** 客户分析面板 */
function CustomerPanel() {
  const [data, setData] = useState<CustomerAnalysisData | null>(null);
  const [statDate, setStatDate] = useState('');

  const load = useCallback(async (value?: string) => {
    try {
      setData(await getCustomerAnalysis(value || undefined));
    } catch (error) {
      message.error(errorText(error));
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <Card
      title="客户分析"
      extra={
        <Space>
          <Input placeholder="统计日 yyyy-MM-dd" value={statDate} onChange={(event) => setStatDate(event.target.value)} style={{ width: 180 }} />
          <Button onClick={() => void load(statDate)}>查询</Button>
        </Space>
      }
    >
      {data ? (
        <>
          <Space size={32} style={{ marginBottom: 16 }}>
            <Statistic title="新增客户" value={data.newCount} />
            <Statistic title="活跃客户（近 30 天有跟进）" value={data.activeCount} />
            <Statistic title="TOP5 集中度" value={percent(data.top5Concentration)} />
          </Space>
          <Row gutter={[16, 16]}>
            <Col xs={24} md={12}><DistributionTable title="等级分布" data={data.levelDistribution} /></Col>
            <Col xs={24} md={12}><DistributionTable title="行业分布" data={data.industryDistribution} /></Col>
            <Col xs={24} md={12}><DistributionTable title="来源分布" data={data.sourceDistribution} /></Col>
            <Col xs={24} md={12}><DistributionTable title="区域分布" data={data.regionDistribution} /></Col>
          </Row>
        </>
      ) : (
        <Typography.Text type="secondary">暂无客户分析数据</Typography.Text>
      )}
    </Card>
  );
}

/** 排行面板 */
function RankingPanel() {
  const [rows, setRows] = useState<RankingRow[]>([]);

  const load = useCallback(async (startDate?: string, endDate?: string) => {
    try {
      setRows(await getRanking(startDate, endDate));
    } catch (error) {
      message.error(errorText(error));
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const columns: TableProps<RankingRow>['columns'] = [
    { title: '排名', dataIndex: 'rank', width: 80 },
    { title: '负责人', dataIndex: 'ownerName', width: 160, render: (value?: string, row?: RankingRow) => value || row?.ownerId || '-' },
    { title: '签单额', dataIndex: 'wonAmount', width: 160, render: (value?: number) => money(value) },
    { title: '赢单数', dataIndex: 'wonCount', width: 100 },
    { title: '合同数', dataIndex: 'contractCount', width: 100 },
    { title: '回款率', dataIndex: 'collectionRate', width: 120, render: (value?: number) => percent(value) },
  ];

  return (
    <Card title="销售排行">
      <Form
        layout="inline"
        style={{ marginBottom: 16 }}
        onFinish={(values) => void load(values.startDate || undefined, values.endDate || undefined)}
      >
        <Form.Item name="startDate"><Input placeholder="开始日期 yyyy-MM-dd" style={{ width: 180 }} /></Form.Item>
        <Form.Item name="endDate"><Input placeholder="结束日期 yyyy-MM-dd" style={{ width: 180 }} /></Form.Item>
        <Form.Item><Button type="primary" htmlType="submit">查询</Button></Form.Item>
      </Form>
      <Table<RankingRow> rowKey="rank" dataSource={rows} columns={columns} pagination={false} />
    </Card>
  );
}

/** 回款分析面板 */
function CollectionPanel() {
  const [data, setData] = useState<CollectionAnalysisData | null>(null);
  const [statDate, setStatDate] = useState('');

  const load = useCallback(async (value?: string) => {
    try {
      setData(await getCollectionAnalysis(value || undefined));
    } catch (error) {
      message.error(errorText(error));
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <Card
      title="回款分析"
      extra={
        <Space>
          <Input placeholder="统计日 yyyy-MM-dd" value={statDate} onChange={(event) => setStatDate(event.target.value)} style={{ width: 180 }} />
          <Button onClick={() => void load(statDate)}>查询</Button>
        </Space>
      }
    >
      {data ? (
        <>
          <Space size={32} style={{ marginBottom: 16 }}>
            <Statistic title="已回款金额" value={money(data.receivedAmount)} />
            <Statistic title="逾期金额" value={money(data.overdueAmount)} />
            <Statistic title="逾期客户数" value={data.overdueCustomerCount} />
          </Space>
          <Row gutter={[16, 16]}>
            <Col xs={24} md={12}>
              <DistributionTable
                title="账龄分布（金额）"
                data={Object.fromEntries(Object.entries(data.agingDistribution ?? {}).map(([key, value]) => [key, Number(value)]))}
              />
            </Col>
            <Col xs={24} md={12}>
              <Card size="small" title="重点逾期客户">
                <Table
                  size="small"
                  rowKey={(row) => `${row?.customerId ?? ''}-${row?.days ?? ''}`}
                  dataSource={data.keyOverdueCustomers}
                  pagination={false}
                  columns={[
                    { title: '客户', dataIndex: 'customerName', render: (value?: string, row?: CollectionAnalysisData['keyOverdueCustomers'][number]) => value || row?.customerId || '-' },
                    { title: '逾期金额', dataIndex: 'overdueAmount', width: 140, render: (value?: number) => money(value) },
                    { title: '逾期天数', dataIndex: 'days', width: 100 },
                  ]}
                />
              </Card>
            </Col>
          </Row>
        </>
      ) : (
        <Typography.Text type="secondary">暂无回款分析数据</Typography.Text>
      )}
    </Card>
  );
}

/** 指标快照面板 */
function SnapshotPanel() {
  const [rows, setRows] = useState<MetricSnapshot[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState({ current: 1, size: 10 });
  const [statDate, setStatDate] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageSnapshots({ ...page });
      setRows(result.records);
      setTotal(Number(result.total));
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    void load();
  }, [load]);

  const generate = async () => {
    try {
      const count = await generateSnapshot(statDate || undefined);
      message.success(`已生成 ${count} 条指标快照`);
      void load();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  return (
    <Card
      title="指标快照"
      extra={
        <Space>
          <Input placeholder="统计日（默认昨日）" value={statDate} onChange={(event) => setStatDate(event.target.value)} style={{ width: 200 }} />
          {hasPermission('sys:crm:analytics:dashboard') && <Button type="primary" onClick={() => void generate()}>生成快照</Button>}
        </Space>
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="快照按「指标 + 日期 + 维度」唯一，重跑覆盖不新增；每日 02:00 自动生成，保留 5 年。"
      />
      <Table<MetricSnapshot>
        rowKey="id"
        loading={loading}
        dataSource={rows}
        pagination={{
          current: page.current,
          pageSize: page.size,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (current, size) => setPage({ current, size }),
        }}
        columns={[
          { title: '指标编码', dataIndex: 'metricCode', width: 200 },
          { title: '统计日', dataIndex: 'statDate', width: 120 },
          { title: '维度类型', dataIndex: 'dimensionType', width: 120 },
          { title: '维度', dataIndex: 'dimensionId', width: 120, render: (value?: string) => value || 'TOTAL' },
          { title: '指标值', dataIndex: 'metricValue', width: 160, render: (value?: number) => (value == null ? '-' : value) },
          { title: '附加信息', dataIndex: 'metricExtra', render: (value?: string) => value || '-' },
        ]}
      />
    </Card>
  );
}

/** 导出面板 */
function ExportPanel() {
  const [task, setTask] = useState<AnalyticsExport | null>(null);
  const [exportType, setExportType] = useState('CUSTOMER');

  const handleCreate = async () => {
    try {
      const created = await createExport(exportType);
      setTask(created);
      message.success(`导出任务已完成：${created.exportNo}`);
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const download = async () => {
    if (!task) {
      return;
    }
    try {
      const { blob, filename } = await downloadExport(task.id);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = filename || `${task.exportNo || 'export'}.csv`;
      link.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      message.error(errorText(error));
    }
  };

  return (
    <Card title="数据导出">
      <Space style={{ marginBottom: 16 }}>
        <Select
          value={exportType}
          onChange={setExportType}
          style={{ width: 220 }}
          options={[
            { value: 'CUSTOMER', label: '客户明细' },
            { value: 'OPPORTUNITY', label: '商机明细' },
            { value: 'ORDER', label: '订单明细' },
            { value: 'COLLECTION', label: '回款明细' },
            { value: 'ACTION', label: '行动项明细' },
          ]}
        />
        {hasPermission('sys:crm:export') && <Button type="primary" onClick={() => void handleCreate()}>创建导出</Button>}
        {task && task.status === 'COMPLETED' && <Button type="primary" ghost onClick={() => void download()}>下载 CSV</Button>}
      </Space>
      {task && (
        <Descriptions bordered size="small" column={2}>
          <Descriptions.Item label="任务号">{task.exportNo}</Descriptions.Item>
          <Descriptions.Item label="类型">{task.exportType}</Descriptions.Item>
          <Descriptions.Item label="状态">
            <Tag color={task.status === 'COMPLETED' ? 'success' : task.status === 'FAILED' ? 'red' : 'processing'}>
              {task.status}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="行数">{task.rowCount ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="文件">{task.fileName || '-'}</Descriptions.Item>
          <Descriptions.Item label="文件 ID">{task.fileId ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="错误" span={2}>{task.error || '-'}</Descriptions.Item>
        </Descriptions>
      )}
      <Typography.Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
        单次导出上限由 whale.crm.analytics.export-max-rows 控制；手机号 / 邮箱默认脱敏。
        导出文件由 whale-file 统一存储，点击“下载 CSV”按 fileId 拉取。
      </Typography.Text>
    </Card>
  );
}

/** 数据分析页（按菜单 tab 渲染） */
export default function AnalyticsPage({ tab }: { tab: AnalyticsTab }) {
  switch (tab) {
    case 'funnel':
      return <FunnelPanel />;
    case 'customer':
      return <CustomerPanel />;
    case 'ranking':
      return <RankingPanel />;
    case 'collection':
      return <CollectionPanel />;
    case 'snapshot':
      return <SnapshotPanel />;
    case 'export':
      return <ExportPanel />;
    default:
      return <DashboardPanel />;
  }
}
