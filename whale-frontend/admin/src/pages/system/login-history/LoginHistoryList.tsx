// 登录历史页 - 列表（按用户/结果/时间过滤）+ CSV 导出
// 权限码：sys:login:history:list(2411) / sys:login:history:export(2412)

import { App, Button, Input, Select, Space, Tag, type TableProps } from 'antd';
import { useRef, useState } from 'react';
import {
  ProTable,
  SearchForm,
  usePermission,
  type ProTableRef,
} from '@bluewhale/whale-ui';
import {
  exportLoginHistories,
  pageLoginHistories,
  type LoginHistoryVO,
} from '../../../api/loginHistory';

type HistoryQuery = { userId?: string; result?: string; startTime?: string; endTime?: string };

const RESULT_OPTIONS = [
  { value: 'SUCCESS', label: '登录成功' },
  { value: 'FAILURE', label: '登录失败' },
  { value: 'LOGOUT', label: '登出' },
];

const RESULT_TAGS: Record<string, { color: string; label: string }> = {
  SUCCESS: { color: 'success', label: '成功' },
  FAILURE: { color: 'error', label: '失败' },
  LOGOUT: { color: 'default', label: '登出' },
};

function renderResult(result: string | undefined) {
  if (!result) return '-';
  const item = RESULT_TAGS[result] ?? { color: 'default', label: result };
  return <Tag color={item.color}>{item.label}</Tag>;
}

function toIsoLocal(dateStr?: string): string | undefined {
  if (!dateStr) return undefined;
  // SearchForm 传 yyyy-MM-dd HH:mm:ss；后端 ISO DATE_TIME 需要 T 分隔
  return dateStr.includes('T') ? dateStr : dateStr.replace(' ', 'T');
}

export default function LoginHistoryList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);

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
      message.success('导出已开始下载');
    } catch (error) {
      showError(error, '导出失败');
    } finally {
      setExporting(false);
    }
  };

  const columns: TableProps<LoginHistoryVO>['columns'] = [
    { title: '用户名', dataIndex: 'username', width: 140, render: (v) => v ?? '-' },
    { title: '用户 ID', dataIndex: 'userId', width: 120, render: (v) => v ?? '-' },
    {
      title: '方式',
      dataIndex: 'loginType',
      width: 110,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '结果',
      dataIndex: 'loginResult',
      width: 90,
      render: (value: string | undefined) => renderResult(value),
    },
    {
      title: '失败原因',
      dataIndex: 'failureReason',
      width: 180,
      ellipsis: true,
      render: (value: string | undefined) => value ?? '-',
    },
    { title: 'IP', dataIndex: 'ip', width: 140, render: (v) => v ?? '-' },
    {
      title: '设备指纹',
      dataIndex: 'deviceFingerprint',
      width: 160,
      ellipsis: true,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '登录时间',
      dataIndex: 'loginTime',
      width: 170,
      render: (value: string | undefined) => value ?? '-',
    },
  ];

  return (
    <ProTable<LoginHistoryVO, HistoryQuery>
      ref={tableRef}
      rowKey="id"
      title="登录历史"
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
      onError={(error) => showError(error, '登录历史加载失败')}
      search={
        <SearchForm
          items={[
            {
              name: 'userId',
              label: '用户 ID',
              element: <Input style={{ width: 180 }} placeholder="留空查全部" />,
            },
            {
              name: 'result',
              label: '结果',
              element: (
                <Select allowClear options={RESULT_OPTIONS} placeholder="全部" style={{ width: 140 }} />
              ),
            },
            {
              name: 'startTime',
              label: '开始时间',
              element: <Input style={{ width: 180 }} placeholder="yyyy-MM-dd HH:mm:ss" />,
            },
            {
              name: 'endTime',
              label: '结束时间',
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
              导出 CSV
            </Button>
          </Space>
        )
      }
    />
  );
}
