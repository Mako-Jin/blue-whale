// 告警中心：告警规则分页表 + 告警记录分页表（Tab 切换）

import { App, Tag, Tabs, type TableProps } from 'antd';
import { ProTable } from '@bluewhale/whale-ui';
import { pageAlertRecord, pageAlertRule, type AlertRecordVO, type AlertRuleVO } from '../api/alert';

const LEVEL_COLOR: Record<string, string> = {
  INFO: 'blue',
  WARN: 'warning',
  WARNING: 'warning',
  ERROR: 'error',
  CRITICAL: 'error',
};

const ruleColumns: TableProps<AlertRuleVO>['columns'] = [
  { title: '规则名称', dataIndex: 'ruleName', width: 180, render: (value?: string) => value ?? '-' },
  { title: '规则编码', dataIndex: 'ruleCode', width: 160, render: (value?: string) => value ?? '-' },
  { title: '监控指标', dataIndex: 'metric', width: 160, render: (value?: string) => value ?? '-' },
  {
    title: '触发条件',
    width: 160,
    render: (_: unknown, record: AlertRuleVO) =>
      record.operator ? `${record.operator} ${record.threshold ?? '-'}` : '-',
  },
  {
    title: '级别',
    dataIndex: 'level',
    width: 100,
    render: (value?: string) => (value ? <Tag color={LEVEL_COLOR[value.toUpperCase()] ?? 'default'}>{value}</Tag> : '-'),
  },
  { title: '通知通道', dataIndex: 'channel', width: 120, render: (value?: string) => value ?? '-' },
  {
    title: '状态',
    dataIndex: 'status',
    width: 100,
    render: (value?: number) => (value === 1 ? <Tag color="success">启用</Tag> : <Tag>停用</Tag>),
  },
  { title: '创建时间', dataIndex: 'createTime', width: 180, render: (value?: string) => value ?? '-' },
];

const recordColumns: TableProps<AlertRecordVO>['columns'] = [
  { title: '规则编码', dataIndex: 'ruleCode', width: 160, render: (value?: string) => value ?? '-' },
  {
    title: '级别',
    dataIndex: 'alertLevel',
    width: 100,
    render: (value?: string) => (value ? <Tag color={LEVEL_COLOR[value.toUpperCase()] ?? 'default'}>{value}</Tag> : '-'),
  },
  { title: '内容', dataIndex: 'content', render: (value?: string) => value ?? '-' },
  { title: '状态', dataIndex: 'status', width: 120, render: (value?: string) => value ?? '-' },
  { title: '触发时间', dataIndex: 'triggerTime', width: 180, render: (value?: string) => value ?? '-' },
  { title: '恢复时间', dataIndex: 'recoverTime', width: 180, render: (value?: string) => value ?? '-' },
];

function RuleTable() {
  const { message } = App.useApp();
  return (
    <ProTable<AlertRuleVO>
      rowKey="id"
      title="告警规则"
      columns={ruleColumns}
      request={pageAlertRule}
      onError={(error) => message.error(error instanceof Error ? error.message : '告警规则加载失败')}
    />
  );
}

function RecordTable() {
  const { message } = App.useApp();
  return (
    <ProTable<AlertRecordVO>
      rowKey="id"
      title="告警记录"
      columns={recordColumns}
      request={pageAlertRecord}
      onError={(error) => message.error(error instanceof Error ? error.message : '告警记录加载失败')}
    />
  );
}

export default function Alert() {
  return (
    <Tabs
      defaultActiveKey="rule"
      items={[
        { key: 'rule', label: '告警规则', children: <RuleTable /> },
        { key: 'record', label: '告警记录', children: <RecordTable /> },
      ]}
    />
  );
}
