// 定时任务管理页表格列定义：任务列表列（工厂函数，注入权限判断与操作回调）、执行日志列、告警记录列

import { Button, Popconfirm, Space, Switch, Tag, Tooltip, Typography, type TableProps } from 'antd';
import { AuthButton } from '@bluewhale/whale-ui';
import type { JobAlertVO, JobInfoVO, JobLogVO } from '../../api/job';
import { ALERT_LEVEL_COLOR, TRIGGER_TYPE_LABELS } from './constants';

/** 日志状态标签：1 成功 / 0 执行中 / -1 失败 */
function renderLogStatus(value: number | undefined, errorMsg?: string) {
  if (value === 1) {
    return <Tag color="success">成功</Tag>;
  }
  if (value === 0) {
    return <Tag color="processing">执行中</Tag>;
  }
  return (
    <Tooltip title={errorMsg ?? '执行失败'}>
      <Tag color="error">失败</Tag>
    </Tooltip>
  );
}

/** 告警状态标签：1 成功 / -1 失败 / 0 待发 */
function renderAlertStatus(value: number | undefined) {
  if (value === 1) {
    return <Tag color="success">成功</Tag>;
  }
  if (value === -1) {
    return <Tag color="error">失败</Tag>;
  }
  return <Tag>待发</Tag>;
}

/** 告警记录表格列（无行内操作，模块级常量即可） */
export const alertColumns: TableProps<JobAlertVO>['columns'] = [
  {
    title: '任务',
    dataIndex: 'jobName',
    width: 180,
    render: (value: string | undefined, row) =>
      value ? `${value}（#${row.jobId}）` : `#${row.jobId}`,
  },
  {
    title: '级别',
    dataIndex: 'level',
    width: 100,
    render: (value: string | undefined) =>
      value ? <Tag color={ALERT_LEVEL_COLOR[value.toUpperCase()] ?? 'default'}>{value}</Tag> : '-',
  },
  {
    title: '渠道',
    dataIndex: 'channel',
    width: 120,
    render: (value: string | undefined) => value ?? '-',
  },
  {
    title: '状态',
    dataIndex: 'status',
    width: 100,
    render: (value: number | undefined) => renderAlertStatus(value),
  },
  {
    title: '错误摘要',
    dataIndex: 'errorMsg',
    ellipsis: true,
    render: (value: string | undefined, row) => value ?? row.message ?? '-',
  },
  {
    title: '时间',
    dataIndex: 'createTime',
    width: 180,
    render: (value: string | undefined) => value ?? '-',
  },
];

/** 执行日志表格列（无行内操作，模块级常量即可） */
export const logColumns: TableProps<JobLogVO>['columns'] = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '任务ID', dataIndex: 'jobId', width: 100 },
  {
    title: '任务名称',
    dataIndex: 'jobName',
    width: 160,
    render: (value: string | undefined) => value ?? '-',
  },
  {
    title: '执行器',
    dataIndex: 'handlerBean',
    width: 180,
    render: (value: string | undefined) => value ?? '-',
  },
  {
    title: '开始时间',
    dataIndex: 'startTime',
    width: 180,
    render: (value: string | undefined) => value ?? '-',
  },
  {
    title: '结束时间',
    dataIndex: 'endTime',
    width: 180,
    render: (value: string | undefined) => value ?? '-',
  },
  {
    title: '状态',
    dataIndex: 'status',
    width: 100,
    render: (value: number | undefined, row) => renderLogStatus(value, row.errorMsg),
  },
  {
    title: '触发方式',
    dataIndex: 'triggerType',
    width: 110,
    render: (value: string | undefined) => (value ? (TRIGGER_TYPE_LABELS[value] ?? value) : '-'),
  },
  {
    title: '批次',
    dataIndex: 'runId',
    width: 180,
    render: (value: string | undefined) =>
      value ? (
        <Tooltip title={value}>
          <Typography.Text
            ellipsis
            style={{ maxWidth: 150, display: 'inline-block', verticalAlign: 'bottom' }}
          >
            {value}
          </Typography.Text>
        </Tooltip>
      ) : (
        '-'
      ),
  },
  {
    title: '结果',
    dataIndex: 'result',
    ellipsis: true,
    render: (value: string | undefined) => value ?? '-',
  },
];

/** 任务列表列的操作回调（由页面容器注入） */
export interface JobColumnHandlers {
  has: (code?: string) => boolean;
  onEdit: (row: JobInfoVO) => void;
  onRun: (row: JobInfoVO) => void;
  onDelete: (row: JobInfoVO) => void;
  onToggle: (row: JobInfoVO, next: number) => void;
}

/** 任务列表表格列：状态开关与操作按钮按权限码渲染，回调由容器注入 */
export function buildJobColumns({
  has,
  onEdit,
  onRun,
  onDelete,
  onToggle,
}: JobColumnHandlers): TableProps<JobInfoVO>['columns'] {
  return [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '任务名称', dataIndex: 'jobName', width: 160 },
    {
      title: '分组',
      dataIndex: 'jobGroup',
      width: 110,
      render: (value: string | undefined) => value ?? '-',
    },
    { title: 'Cron', dataIndex: 'cronExpression', width: 150 },
    { title: '执行器', dataIndex: 'handlerBean', width: 180 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: number | undefined, row) => (
        <Switch
          checked={value === 1}
          disabled={!has('sys:job:edit')}
          onChange={(checked) => void onToggle(row, checked ? 1 : 0)}
        />
      ),
    },
    {
      title: '下次执行时间',
      dataIndex: 'nextFireTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 240,
      render: (_, row) => (
        <Space size={4} wrap>
          <AuthButton perm="sys:job:edit" type="link" size="small" onClick={() => onEdit(row)}>
            编辑
          </AuthButton>
          {has('sys:job:run') && (
            <Popconfirm title="确认立即执行该任务？" onConfirm={() => void onRun(row)}>
              <Button type="link" size="small">
                立即执行
              </Button>
            </Popconfirm>
          )}
          {has('sys:job:delete') && (
            <Popconfirm title="确认删除该任务？" onConfirm={() => void onDelete(row)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];
}
