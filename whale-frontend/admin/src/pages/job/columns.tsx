// 定时任务管理页表格列定义：任务列表列（工厂函数，注入权限判断与操作回调）、执行日志列、告警记录列

import { Button, Popconfirm, Space, Switch, Tag, Tooltip, Typography, type TableProps } from 'antd';
import { AuthButton } from '@bluewhale/whale-ui';
import type { MessageKey } from '../../i18n/messages';
import type { JobAlertVO, JobInfoVO, JobLogVO } from '../../api/job';
import { ALERT_LEVEL_COLOR, buildTriggerTypeLabels } from './constants';

/** 翻译函数签名（由 useI18n 注入） */
type Translate = (key: MessageKey, vars?: Record<string, string | number>) => string;

/** 日志状态标签：1 成功 / 0 执行中 / -1 失败 */
function renderLogStatus(t: Translate, value: number | undefined, errorMsg?: string) {
  if (value === 1) {
    return <Tag color="success">{t('job.log.status.success')}</Tag>;
  }
  if (value === 0) {
    return <Tag color="processing">{t('job.log.status.running')}</Tag>;
  }
  return (
    <Tooltip title={errorMsg ?? t('job.log.status.failedTip')}>
      <Tag color="error">{t('job.log.status.failed')}</Tag>
    </Tooltip>
  );
}

/** 告警状态标签：1 成功 / -1 失败 / 0 待发 */
function renderAlertStatus(t: Translate, value: number | undefined) {
  if (value === 1) {
    return <Tag color="success">{t('job.alert.status.success')}</Tag>;
  }
  if (value === -1) {
    return <Tag color="error">{t('job.alert.status.failed')}</Tag>;
  }
  return <Tag>{t('job.alert.status.pending')}</Tag>;
}

/** 告警记录表格列（无行内操作，按翻译函数生成） */
export function buildAlertColumns(t: Translate): TableProps<JobAlertVO>['columns'] {
  return [
    {
      title: t('job.alert.col.job'),
      dataIndex: 'jobName',
      width: 180,
      render: (value: string | undefined, row) =>
        value ? `${value}（#${row.jobId}）` : `#${row.jobId}`,
    },
    {
      title: t('job.alert.col.level'),
      dataIndex: 'level',
      width: 140,
      render: (value: string | undefined, row) => (
        <Space size={4} wrap>
          {value ? (
            <Tag color={ALERT_LEVEL_COLOR[value.toUpperCase()] ?? 'default'}>{value}</Tag>
          ) : (
            '-'
          )}
          {row.recovered && <Tag color="success">{t('job.alert.recovered')}</Tag>}
        </Space>
      ),
    },
    {
      title: t('job.alert.col.channel'),
      dataIndex: 'channel',
      width: 180,
      render: (value: string | undefined) => {
        const channels = (value ?? '')
          .split(',')
          .map((channel) => channel.trim())
          .filter(Boolean);
        if (channels.length === 0) {
          return '-';
        }
        return (
          <Space size={4} wrap>
            {channels.map((channel) => (
              <Tag key={channel}>{channel}</Tag>
            ))}
          </Space>
        );
      },
    },
    {
      title: t('job.alert.col.status'),
      dataIndex: 'status',
      width: 100,
      render: (value: number | undefined) => renderAlertStatus(t, value),
    },
    {
      title: t('job.alert.col.errorMsg'),
      dataIndex: 'errorMsg',
      ellipsis: true,
      render: (value: string | undefined, row) => value ?? row.message ?? '-',
    },
    {
      title: t('job.alert.col.time'),
      dataIndex: 'createTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
  ];
}

/** 执行日志表格列（无行内操作，按翻译函数生成） */
export function buildLogColumns(t: Translate): TableProps<JobLogVO>['columns'] {
  return [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: t('job.log.col.jobId'), dataIndex: 'jobId', width: 100 },
    {
      title: t('job.log.col.jobName'),
      dataIndex: 'jobName',
      width: 160,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('job.log.col.handler'),
      dataIndex: 'handlerBean',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('job.log.col.startTime'),
      dataIndex: 'startTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('job.log.col.endTime'),
      dataIndex: 'endTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('job.log.col.status'),
      dataIndex: 'status',
      width: 100,
      render: (value: number | undefined, row) => renderLogStatus(t, value, row.errorMsg),
    },
    {
      title: t('job.log.col.triggerType'),
      dataIndex: 'triggerType',
      width: 110,
      render: (value: string | undefined) =>
        value ? (buildTriggerTypeLabels(t)[value] ?? value) : '-',
    },
    {
      title: t('job.log.col.runId'),
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
      title: t('job.log.col.result'),
      dataIndex: 'result',
      ellipsis: true,
      render: (value: string | undefined) => value ?? '-',
    },
  ];
}

/** 任务列表列的操作回调（由页面容器注入） */
export interface JobColumnHandlers {
  t: Translate;
  has: (code?: string) => boolean;
  onEdit: (row: JobInfoVO) => void;
  onRun: (row: JobInfoVO) => void;
  onDelete: (row: JobInfoVO) => void;
  onToggle: (row: JobInfoVO, next: number) => void;
}

/** 任务列表表格列：状态开关与操作按钮按权限码渲染，回调由容器注入 */
export function buildJobColumns({
  t,
  has,
  onEdit,
  onRun,
  onDelete,
  onToggle,
}: JobColumnHandlers): TableProps<JobInfoVO>['columns'] {
  return [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: t('job.list.col.jobName'), dataIndex: 'jobName', width: 160 },
    {
      title: t('job.list.col.jobGroup'),
      dataIndex: 'jobGroup',
      width: 110,
      render: (value: string | undefined) => value ?? '-',
    },
    { title: 'Cron', dataIndex: 'cronExpression', width: 150 },
    { title: t('job.list.col.handler'), dataIndex: 'handlerBean', width: 180 },
    {
      title: t('job.list.col.status'),
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
      title: t('job.list.col.nextFireTime'),
      dataIndex: 'nextFireTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('job.list.col.action'),
      key: 'action',
      width: 240,
      render: (_, row) => (
        <Space size={4} wrap>
          <AuthButton perm="sys:job:edit" type="link" size="small" onClick={() => onEdit(row)}>
            {t('job.list.action.edit')}
          </AuthButton>
          {has('sys:job:run') && (
            <Popconfirm
              title={t('job.list.confirm.run')}
              onConfirm={() => void onRun(row)}
            >
              <Button type="link" size="small">
                {t('job.list.action.run')}
              </Button>
            </Popconfirm>
          )}
          {has('sys:job:delete') && (
            <Popconfirm
              title={t('job.list.confirm.delete')}
              onConfirm={() => void onDelete(row)}
            >
              <Button type="link" size="small" danger>
                {t('job.list.action.delete')}
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];
}
