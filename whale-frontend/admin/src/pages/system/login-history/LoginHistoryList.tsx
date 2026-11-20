// 登录历史页 - 列表（按用户/结果/时间过滤）+ CSV 导出
// 权限码：sys:login:history:list(2411) / sys:login:history:export(2412)

import { App, Button, Input, Select, Space, Tag, type TableProps } from 'antd';
import { useRef, useState } from 'react';
import { ProTable, SearchForm, usePermission, type ProTableRef } from '@bluewhale/whale-ui';
import {
  exportLoginHistories,
  pageLoginHistories,
  type LoginHistoryVO,
} from '../../../api/loginHistory';
import { useI18n } from '../../../i18n';

type HistoryQuery = { userId?: string; result?: string; startTime?: string; endTime?: string };

type Translate = ReturnType<typeof useI18n>['t'];

const buildResultOptions = (t: Translate) => [
  { value: 'SUCCESS', label: t('systemAuth.loginHistory.result.loginSuccess') },
  { value: 'FAILURE', label: t('systemAuth.loginHistory.result.loginFailure') },
  { value: 'LOGOUT', label: t('systemAuth.loginHistory.result.logout') },
];

const buildResultTags = (t: Translate): Record<string, { color: string; label: string }> => ({
  SUCCESS: { color: 'success', label: t('systemAuth.loginHistory.status.success') },
  FAILURE: { color: 'error', label: t('systemAuth.loginHistory.status.failure') },
  LOGOUT: { color: 'default', label: t('systemAuth.loginHistory.result.logout') },
});

function renderResult(
  result: string | undefined,
  resultTags: Record<string, { color: string; label: string }>,
) {
  if (!result) return '-';
  const item = resultTags[result] ?? { color: 'default', label: result };
  return <Tag color={item.color}>{item.label}</Tag>;
}

function toIsoLocal(dateStr?: string): string | undefined {
  if (!dateStr) return undefined;
  // SearchForm 传 yyyy-MM-dd HH:mm:ss；后端 ISO DATE_TIME 需要 T 分隔
  return dateStr.includes('T') ? dateStr : dateStr.replace(' ', 'T');
}

export default function LoginHistoryList() {
  const { message } = App.useApp();
  const { t } = useI18n();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);

  const resultOptions = buildResultOptions(t);
  const resultTags = buildResultTags(t);

  const [userId, setUserId] = useState<string | undefined>(undefined);
  const [result, setResult] = useState<string | undefined>(undefined);
  const [startTime, setStartTime] = useState<string | undefined>(undefined);
  const [endTime, setEndTime] = useState<string | undefined>(undefined);
  const [exporting, setExporting] = useState(false);

  const showError = (error: unknown, fallback: string) => {
    message.error(error instanceof Error ? error.message : fallback);
  };

  const handleExport = async () => {
    setExporting(true);
    try {
      await exportLoginHistories({
        userId,
        result,
        startTime: toIsoLocal(startTime),
        endTime: toIsoLocal(endTime),
      });
      message.success(t('systemAuth.loginHistory.exportStarted'));
    } catch (error) {
      showError(error, t('systemAuth.loginHistory.exportFailed'));
    } finally {
      setExporting(false);
    }
  };

  const columns: TableProps<LoginHistoryVO>['columns'] = [
    {
      title: t('systemAuth.loginHistory.col.username'),
      dataIndex: 'username',
      width: 140,
      render: (v) => v ?? '-',
    },
    {
      title: t('systemAuth.loginHistory.col.userId'),
      dataIndex: 'userId',
      width: 120,
      render: (v) => v ?? '-',
    },
    {
      title: t('systemAuth.loginHistory.col.loginType'),
      dataIndex: 'loginType',
      width: 110,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('systemAuth.loginHistory.col.result'),
      dataIndex: 'loginResult',
      width: 90,
      render: (value: string | undefined) => renderResult(value, resultTags),
    },
    {
      title: t('systemAuth.loginHistory.col.failureReason'),
      dataIndex: 'failureReason',
      width: 180,
      ellipsis: true,
      render: (value: string | undefined) => value ?? '-',
    },
    { title: 'IP', dataIndex: 'ip', width: 140, render: (v) => v ?? '-' },
    {
      title: t('systemAuth.loginHistory.col.deviceFingerprint'),
      dataIndex: 'deviceFingerprint',
      width: 160,
      ellipsis: true,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('systemAuth.loginHistory.col.loginTime'),
      dataIndex: 'loginTime',
      width: 170,
      render: (value: string | undefined) => value ?? '-',
    },
  ];

  return (
    <ProTable<LoginHistoryVO, HistoryQuery>
      ref={tableRef}
      rowKey="id"
      title={t('systemAuth.loginHistory.title')}
      columns={columns}
      request={(params) =>
        pageLoginHistories({
          ...params,
          userId,
          startTime: toIsoLocal(startTime),
          endTime: toIsoLocal(endTime),
        })
      }
      params={{ userId, result }}
      onError={(error) => showError(error, t('systemAuth.loginHistory.loadFailed'))}
      search={
        <SearchForm
          items={[
            {
              name: 'userId',
              label: t('systemAuth.loginHistory.col.userId'),
              element: (
                <Input
                  style={{ width: 180 }}
                  placeholder={t('systemAuth.loginHistory.search.userIdPlaceholder')}
                />
              ),
            },
            {
              name: 'result',
              label: t('systemAuth.loginHistory.col.result'),
              element: (
                <Select
                  allowClear
                  options={resultOptions}
                  placeholder={t('systemAuth.loginHistory.search.all')}
                  style={{ width: 140 }}
                />
              ),
            },
            {
              name: 'startTime',
              label: t('systemAuth.loginHistory.search.startTime'),
              element: <Input style={{ width: 180 }} placeholder="yyyy-MM-dd HH:mm:ss" />,
            },
            {
              name: 'endTime',
              label: t('systemAuth.loginHistory.search.endTime'),
              element: <Input style={{ width: 180 }} placeholder="yyyy-MM-dd HH:mm:ss" />,
            },
          ]}
          onSearch={(values) => {
            setUserId((values.userId as string | undefined) || undefined);
            setResult((values.result as string) || undefined);
            setStartTime((values.startTime as string) || undefined);
            setEndTime((values.endTime as string) || undefined);
          }}
          onReset={() => {
            setUserId(undefined);
            setResult(undefined);
            setStartTime(undefined);
            setEndTime(undefined);
          }}
        />
      }
      toolbar={
        has('sys:login:history:export') && (
          <Space>
            <Button loading={exporting} onClick={() => void handleExport()}>
              {t('systemAuth.loginHistory.export')}
            </Button>
          </Space>
        )
      }
    />
  );
}
