// ERP 数据分析与报表中心页：经营驾驶舱、多维分析、报表中心、预警中心
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Col,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  message,
} from 'antd';
import type { TableProps, TabsProps } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  ALERT_LEVEL_LABEL,
  ALERT_STATUS_LABEL,
  DIMENSION_LABEL,
  METRIC_LABEL,
  RULE_TYPE_LABEL,
  acknowledgeAlert,
  closeAlert,
  createAlertRule,
  createReportDefinition,
  createReportSchedule,
  evaluateAlerts,
  executeReport,
  exportReportCsv,
  getCockpit,
  invalidateCockpit,
  pageAlertEvents,
  pageAlertRules,
  pageReportDefinitions,
  pageReportSchedules,
  queryCube,
  refreshCockpit,
  runReportSchedule,
  updateAlertRule,
  updateReportDefinition,
  updateReportSchedule,
  type AlertEventView,
  type AlertRuleView,
  type CockpitView,
  type CubeResult,
  type ReportDefinitionView,
  type ReportScheduleView,
} from '../api/bi';
import { hasPermission } from '../utils/permission';

/** 当前期间 yyyyMM */
function currentPeriod(): string {
  return new Date().toISOString().slice(0, 7).replace('-', '');
}

/** 金额格式化 */
function money(value: number | undefined): string {
  return Number(value ?? 0).toFixed(2);
}

/** 下载文本为文件 */
function downloadText(fileName: string, content: string): void {
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  anchor.click();
  URL.revokeObjectURL(url);
}

/** 驾驶舱页签 */
function CockpitTab() {
  const [period, setPeriod] = useState(currentPeriod());
  const [cockpit, setCockpit] = useState<CockpitView | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setCockpit(await getCockpit(period));
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [period]);

  useEffect(() => {
    void load();
  }, [load]);

  const refresh = async () => {
    setLoading(true);
    try {
      setCockpit(await refreshCockpit({ periodCode: period }));
      message.success('驾驶舱快照已刷新');
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  };

  const invalidate = async () => {
    try {
      await invalidateCockpit(period);
      message.success('快照已失效，下次查询将重新计算');
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input
          style={{ width: 140 }}
          value={period}
          placeholder="统计期间 yyyyMM"
          onChange={(event) => setPeriod(event.target.value)}
        />
        <Button loading={loading} onClick={() => void load()}>
          查询
        </Button>
        {hasPermission('sys:erp:bi:cockpit:refresh') ? (
          <>
            <Button type="primary" onClick={() => void refresh()}>
              刷新快照
            </Button>
            <Button onClick={() => void invalidate()}>失效缓存</Button>
          </>
        ) : null}
        {cockpit ? (
          <Tag color={cockpit.status === 'ACTIVE' ? 'green' : 'orange'}>{cockpit.status}</Tag>
        ) : null}
      </Space>
      <Row gutter={16}>
        <Col span={6}>
          <Card>
            <Statistic title="收入" value={money(cockpit?.revenueAmount)} suffix="元" />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="成本" value={money(cockpit?.costAmount)} suffix="元" />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="利润" value={money(cockpit?.profitAmount)} suffix="元" />
          </Card>
        </Col>
        <Col span={3}>
          <Card>
            <Statistic title="订单数" value={cockpit?.orderCount ?? 0} />
          </Card>
        </Col>
        <Col span={3}>
          <Card>
            <Statistic title="未关闭预警" value={cockpit?.alertCount ?? 0} />
          </Card>
        </Col>
      </Row>
      {cockpit ? (
        <Card size="small">
          快照号：{cockpit.snapshotNo}，统计截止：{cockpit.statDate}，刷新时间：
          {cockpit.refreshedAt}
        </Card>
      ) : null}
    </Space>
  );
}

/** 多维分析页签 */
function CubeTab() {
  const [dimension, setDimension] = useState('CUSTOMER');
  const [metric, setMetric] = useState('REVENUE');
  const [range, setRange] = useState<[Dayjs, Dayjs]>([dayjs().startOf('month'), dayjs()]);
  const [result, setResult] = useState<CubeResult | null>(null);
  const [loading, setLoading] = useState(false);

  const query = async () => {
    setLoading(true);
    try {
      setResult(
        await queryCube({
          dimensionType: dimension,
          metricType: metric,
          fromDate: range[0].format('YYYY-MM-DD'),
          toDate: range[1].format('YYYY-MM-DD'),
        }),
      );
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void query();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const columns: TableProps<CubeResult['rows'][number]>['columns'] = [
    { title: '维度键', dataIndex: 'dimensionKey', key: 'dimensionKey', width: 180 },
    { title: '名称', dataIndex: 'dimensionName', key: 'dimensionName' },
    {
      title: '指标值',
      dataIndex: 'metricValue',
      key: 'metricValue',
      width: 160,
      render: (value: number) => money(value),
    },
    { title: '订单数', dataIndex: 'orderCount', key: 'orderCount', width: 100 },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Select
          style={{ width: 160 }}
          value={dimension}
          onChange={setDimension}
          options={Object.entries(DIMENSION_LABEL).map(([value, label]) => ({ value, label }))}
        />
        <Select
          style={{ width: 160 }}
          value={metric}
          onChange={setMetric}
          options={Object.entries(METRIC_LABEL).map(([value, label]) => ({ value, label }))}
        />
        <DatePicker.RangePicker
          value={range}
          onChange={(value) => value?.[0] && value[1] && setRange([value[0], value[1]])}
        />
        <Button type="primary" loading={loading} onClick={() => void query()}>
          分析
        </Button>
      </Space>
      {result?.note ? <Tag color="orange">{result.note}</Tag> : null}
      <Table
        rowKey="dimensionKey"
        loading={loading}
        columns={columns}
        dataSource={result?.rows ?? []}
        pagination={false}
        summary={() => (
          <Table.Summary.Row>
            <Table.Summary.Cell index={0}>合计</Table.Summary.Cell>
            <Table.Summary.Cell index={1}>
              {result ? `订单 ${result.totalOrderCount}` : ''}
            </Table.Summary.Cell>
            <Table.Summary.Cell index={2}>{money(result?.totalMetric)}</Table.Summary.Cell>
            <Table.Summary.Cell index={3} />
          </Table.Summary.Row>
        )}
      />
    </Space>
  );
}

/** 报表中心页签 */
function ReportTab() {
  const [definitions, setDefinitions] = useState<ReportDefinitionView[]>([]);
  const [schedules, setSchedules] = useState<ReportScheduleView[]>([]);
  const [loading, setLoading] = useState(false);
  const [definitionOpen, setDefinitionOpen] = useState(false);
  const [scheduleOpen, setScheduleOpen] = useState(false);
  const [editingDefinition, setEditingDefinition] = useState<ReportDefinitionView | null>(null);
  const [editingSchedule, setEditingSchedule] = useState<ReportScheduleView | null>(null);
  const [definitionForm] = Form.useForm();
  const [scheduleForm] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [definitionPage, schedulePage] = await Promise.all([
        pageReportDefinitions({ size: 100 }),
        pageReportSchedules({ size: 100 }),
      ]);
      setDefinitions(definitionPage.records);
      setSchedules(schedulePage.records);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const openDefinition = (record: ReportDefinitionView | null) => {
    setEditingDefinition(record);
    definitionForm.resetFields();
    if (record) {
      definitionForm.setFieldsValue(record);
    } else {
      definitionForm.setFieldsValue({
        reportType: 'CUSTOM',
        status: 'ENABLED',
        dimensionType: 'CUSTOMER',
        metricType: 'REVENUE',
      });
    }
    setDefinitionOpen(true);
  };

  const saveDefinition = async () => {
    const values = await definitionForm.validateFields();
    try {
      if (editingDefinition) {
        await updateReportDefinition(editingDefinition.reportNo, values);
      } else {
        await createReportDefinition(values);
      }
      message.success('报表定义已保存');
      setDefinitionOpen(false);
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const execute = async (record: ReportDefinitionView) => {
    try {
      const result = await executeReport(record.reportNo);
      message.success(`执行完成：${result.rows.length} 行，合计 ${money(result.totalMetric)}`);
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const exportCsv = async (record: ReportDefinitionView) => {
    try {
      const csv = await exportReportCsv(record.reportNo);
      downloadText(`${record.reportNo}.csv`, csv);
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const openSchedule = (record: ReportScheduleView | null) => {
    setEditingSchedule(record);
    scheduleForm.resetFields();
    if (record) {
      scheduleForm.setFieldsValue(record);
    } else {
      scheduleForm.setFieldsValue({ channel: 'INTERNAL', status: 'ENABLED' });
    }
    setScheduleOpen(true);
  };

  const saveSchedule = async () => {
    const values = await scheduleForm.validateFields();
    try {
      if (editingSchedule) {
        await updateReportSchedule(editingSchedule.scheduleNo, values);
      } else {
        await createReportSchedule(values);
      }
      message.success('报表计划已保存');
      setScheduleOpen(false);
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const runSchedule = async (record: ReportScheduleView) => {
    try {
      await runReportSchedule(record.scheduleNo);
      message.success('计划已执行');
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const definitionColumns: TableProps<ReportDefinitionView>['columns'] = [
    { title: '报表编号', dataIndex: 'reportNo', key: 'reportNo', width: 160 },
    { title: '名称', dataIndex: 'reportName', key: 'reportName' },
    {
      title: '维度',
      dataIndex: 'dimensionType',
      key: 'dimensionType',
      width: 110,
      render: (value: string) => DIMENSION_LABEL[value] ?? value,
    },
    {
      title: '指标',
      dataIndex: 'metricType',
      key: 'metricType',
      width: 110,
      render: (value: string) => METRIC_LABEL[value] ?? value,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (value: string) => (
        <Tag color={value === 'ENABLED' ? 'green' : 'default'}>{value}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 260,
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => void execute(record)}>
            执行
          </Button>
          {hasPermission('sys:erp:bi:report:export') ? (
            <Button size="small" onClick={() => void exportCsv(record)}>
              导出
            </Button>
          ) : null}
          {hasPermission('sys:erp:bi:report:save') ? (
            <Button size="small" onClick={() => openDefinition(record)}>
              编辑
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  const scheduleColumns: TableProps<ReportScheduleView>['columns'] = [
    { title: '计划编号', dataIndex: 'scheduleNo', key: 'scheduleNo', width: 150 },
    { title: '名称', dataIndex: 'scheduleName', key: 'scheduleName' },
    { title: '报表编号', dataIndex: 'reportNo', key: 'reportNo', width: 150 },
    { title: 'Cron', dataIndex: 'cronExpression', key: 'cronExpression', width: 140 },
    { title: '渠道', dataIndex: 'channel', key: 'channel', width: 100 },
    { title: '上次执行', dataIndex: 'lastRunTime', key: 'lastRunTime', width: 170 },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_, record) => (
        <Space>
          {hasPermission('sys:erp:bi:report:run') ? (
            <Button size="small" onClick={() => void runSchedule(record)}>
              执行一次
            </Button>
          ) : null}
          {hasPermission('sys:erp:bi:report:save') ? (
            <Button size="small" onClick={() => openSchedule(record)}>
              编辑
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space>
        {hasPermission('sys:erp:bi:report:save') ? (
          <Button type="primary" onClick={() => openDefinition(null)}>
            新建报表
          </Button>
        ) : null}
        {hasPermission('sys:erp:bi:report:save') ? (
          <Button onClick={() => openSchedule(null)}>新建计划</Button>
        ) : null}
      </Space>
      <Card title="报表定义" size="small">
        <Table
          rowKey="reportNo"
          loading={loading}
          columns={definitionColumns}
          dataSource={definitions}
          pagination={false}
        />
      </Card>
      <Card title="定时计划" size="small">
        <Table
          rowKey="scheduleNo"
          loading={loading}
          columns={scheduleColumns}
          dataSource={schedules}
          pagination={false}
        />
      </Card>
      <Modal
        title={editingDefinition ? '编辑报表定义' : '新建报表定义'}
        open={definitionOpen}
        onCancel={() => setDefinitionOpen(false)}
        onOk={() => void saveDefinition()}
        destroyOnClose
      >
        <Form form={definitionForm} layout="vertical">
          <Form.Item
            name="reportNo"
            label="报表编号"
            rules={[{ required: true, message: '请输入报表编号' }]}
          >
            <Input disabled={Boolean(editingDefinition)} />
          </Form.Item>
          <Form.Item
            name="reportName"
            label="报表名称"
            rules={[{ required: true, message: '请输入报表名称' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item name="dimensionType" label="维度" rules={[{ required: true }]}>
            <Select
              options={Object.entries(DIMENSION_LABEL).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
          <Form.Item name="metricType" label="指标" rules={[{ required: true }]}>
            <Select
              options={Object.entries(METRIC_LABEL).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select
              options={[
                { value: 'ENABLED', label: '启用' },
                { value: 'DISABLED', label: '停用' },
              ]}
            />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title={editingSchedule ? '编辑报表计划' : '新建报表计划'}
        open={scheduleOpen}
        onCancel={() => setScheduleOpen(false)}
        onOk={() => void saveSchedule()}
        destroyOnClose
      >
        <Form form={scheduleForm} layout="vertical">
          <Form.Item
            name="scheduleNo"
            label="计划编号"
            rules={[{ required: true, message: '请输入计划编号' }]}
          >
            <Input disabled={Boolean(editingSchedule)} />
          </Form.Item>
          <Form.Item
            name="scheduleName"
            label="计划名称"
            rules={[{ required: true, message: '请输入计划名称' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="reportNo"
            label="报表编号"
            rules={[{ required: true, message: '请输入报表编号' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="cronExpression"
            label="Cron 表达式"
            rules={[{ required: true, message: '请输入 Cron' }]}
          >
            <Input placeholder="0 8 * * *" />
          </Form.Item>
          <Form.Item name="channel" label="推送渠道">
            <Select
              options={[
                { value: 'INTERNAL', label: '站内' },
                { value: 'EMAIL', label: '邮件' },
                { value: 'MESSAGE', label: '消息' },
              ]}
            />
          </Form.Item>
          <Form.Item name="recipients" label="接收人">
            <Input />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select
              options={[
                { value: 'ENABLED', label: '启用' },
                { value: 'DISABLED', label: '停用' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

/** 预警中心页签 */
function AlertTab() {
  const [rules, setRules] = useState<AlertRuleView[]>([]);
  const [events, setEvents] = useState<AlertEventView[]>([]);
  const [loading, setLoading] = useState(false);
  const [ruleOpen, setRuleOpen] = useState(false);
  const [editingRule, setEditingRule] = useState<AlertRuleView | null>(null);
  const [ruleForm] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [rulePage, eventPage] = await Promise.all([
        pageAlertRules({ size: 100 }),
        pageAlertEvents({ size: 100 }),
      ]);
      setRules(rulePage.records);
      setEvents(eventPage.records);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const openRule = (record: AlertRuleView | null) => {
    setEditingRule(record);
    ruleForm.resetFields();
    if (record) {
      ruleForm.setFieldsValue(record);
    } else {
      ruleForm.setFieldsValue({
        ruleType: 'STOCK_LOW',
        periodType: 'DAY',
        level: 'WARN',
        status: 'ENABLED',
        thresholdValue: 0,
      });
    }
    setRuleOpen(true);
  };

  const saveRule = async () => {
    const values = await ruleForm.validateFields();
    try {
      if (editingRule) {
        await updateAlertRule(editingRule.ruleNo, values);
      } else {
        await createAlertRule(values);
      }
      message.success('预警规则已保存');
      setRuleOpen(false);
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const evaluate = async () => {
    try {
      const result = await evaluateAlerts();
      message.success(
        `评估 ${result.evaluatedRules} 条规则，新增 ${result.createdEvents} 条，去重 ${result.duplicateSkipped} 条`,
      );
      if (result.note) {
        message.info(result.note);
      }
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const handleEvent = async (id: number, action: 'ack' | 'close') => {
    try {
      if (action === 'ack') {
        await acknowledgeAlert(id);
      } else {
        await closeAlert(id);
      }
      message.success('预警事件已处理');
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const ruleColumns: TableProps<AlertRuleView>['columns'] = [
    { title: '规则编号', dataIndex: 'ruleNo', key: 'ruleNo', width: 150 },
    { title: '名称', dataIndex: 'ruleName', key: 'ruleName' },
    {
      title: '类型',
      dataIndex: 'ruleType',
      key: 'ruleType',
      width: 120,
      render: (value: string) => RULE_TYPE_LABEL[value] ?? value,
    },
    { title: '阈值', dataIndex: 'thresholdValue', key: 'thresholdValue', width: 100 },
    { title: '周期', dataIndex: 'periodType', key: 'periodType', width: 90 },
    {
      title: '级别',
      dataIndex: 'level',
      key: 'level',
      width: 90,
      render: (value: string) => <Tag>{ALERT_LEVEL_LABEL[value] ?? value}</Tag>,
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: (_, record) =>
        hasPermission('sys:erp:bi:alert:save') ? (
          <Button size="small" onClick={() => openRule(record)}>
            编辑
          </Button>
        ) : null,
    },
  ];

  const eventColumns: TableProps<AlertEventView>['columns'] = [
    { title: '事件编号', dataIndex: 'eventNo', key: 'eventNo', width: 170 },
    { title: '规则', dataIndex: 'ruleNo', key: 'ruleNo', width: 120 },
    {
      title: '类型',
      dataIndex: 'ruleType',
      key: 'ruleType',
      width: 120,
      render: (value: string) => RULE_TYPE_LABEL[value] ?? value,
    },
    { title: '对象', dataIndex: 'objectKey', key: 'objectKey', width: 160 },
    { title: '周期', dataIndex: 'periodCode', key: 'periodCode', width: 110 },
    {
      title: '实际/阈值',
      key: 'metric',
      width: 130,
      render: (_, record) => `${record.metricValue} / ${record.thresholdValue}`,
    },
    {
      title: '级别',
      dataIndex: 'level',
      key: 'level',
      width: 90,
      render: (value: string) => (
        <Tag color={value === 'CRITICAL' ? 'red' : 'orange'}>
          {ALERT_LEVEL_LABEL[value] ?? value}
        </Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: string) => (
        <Tag color={value === 'OPEN' ? 'orange' : 'green'}>
          {ALERT_STATUS_LABEL[value] ?? value}
        </Tag>
      ),
    },
    { title: '触发时间', dataIndex: 'triggeredAt', key: 'triggeredAt', width: 170 },
    {
      title: '操作',
      key: 'action',
      width: 170,
      render: (_, record) =>
        hasPermission('sys:erp:bi:alert:handle') && record.status !== 'CLOSED' ? (
          <Space>
            {record.status === 'OPEN' ? (
              <Button size="small" onClick={() => void handleEvent(record.id, 'ack')}>
                确认
              </Button>
            ) : null}
            <Button size="small" onClick={() => void handleEvent(record.id, 'close')}>
              关闭
            </Button>
          </Space>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space>
        {hasPermission('sys:erp:bi:alert:save') ? (
          <Button type="primary" onClick={() => openRule(null)}>
            新建规则
          </Button>
        ) : null}
        {hasPermission('sys:erp:bi:alert:evaluate') ? (
          <Button onClick={() => void evaluate()}>立即评估</Button>
        ) : null}
      </Space>
      <Card title="预警规则" size="small">
        <Table
          rowKey="ruleNo"
          loading={loading}
          columns={ruleColumns}
          dataSource={rules}
          pagination={false}
        />
      </Card>
      <Card title="预警事件" size="small">
        <Table
          rowKey="id"
          loading={loading}
          columns={eventColumns}
          dataSource={events}
          pagination={false}
        />
      </Card>
      <Modal
        title={editingRule ? '编辑预警规则' : '新建预警规则'}
        open={ruleOpen}
        onCancel={() => setRuleOpen(false)}
        onOk={() => void saveRule()}
        destroyOnClose
      >
        <Form form={ruleForm} layout="vertical">
          <Form.Item
            name="ruleNo"
            label="规则编号"
            rules={[{ required: true, message: '请输入规则编号' }]}
          >
            <Input disabled={Boolean(editingRule)} />
          </Form.Item>
          <Form.Item
            name="ruleName"
            label="规则名称"
            rules={[{ required: true, message: '请输入规则名称' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item name="ruleType" label="预警类型" rules={[{ required: true }]}>
            <Select
              options={Object.entries(RULE_TYPE_LABEL).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
          <Form.Item name="thresholdValue" label="阈值（库存数量/超期天数）">
            <InputNumber style={{ width: '100%' }} min={0} />
          </Form.Item>
          <Form.Item name="periodType" label="统计周期">
            <Select
              options={[
                { value: 'DAY', label: '按日' },
                { value: 'WEEK', label: '按周' },
                { value: 'MONTH', label: '按月' },
              ]}
            />
          </Form.Item>
          <Form.Item name="level" label="级别">
            <Select
              options={Object.entries(ALERT_LEVEL_LABEL).map(([value, label]) => ({
                value,
                label,
              }))}
            />
          </Form.Item>
          <Form.Item name="suggestion" label="计划建议">
            <Input.TextArea rows={2} placeholder="如：补货建议 / 催收计划" />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select
              options={[
                { value: 'ENABLED', label: '启用' },
                { value: 'DISABLED', label: '停用' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

/** 分析与报表中心主页面 */
export default function AnalyticsPage({ initialTab = 'cockpit' }: { initialTab?: string }) {
  const items: TabsProps['items'] = [
    { key: 'cockpit', label: '经营驾驶舱', children: <CockpitTab /> },
    { key: 'cube', label: '多维分析', children: <CubeTab /> },
    { key: 'report', label: '报表中心', children: <ReportTab /> },
    { key: 'alert', label: '预警中心', children: <AlertTab /> },
  ];
  return <Tabs defaultActiveKey={initialTab} items={items} />;
}
