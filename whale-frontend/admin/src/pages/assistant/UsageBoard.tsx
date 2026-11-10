// AI 助手用量看板页 - 助手×天聚合（会话数 / 消息数 / token），按日期倒序
// 权限码：sys:assistant:define(2300)

import { App, DatePicker, Input, Tag, type TableProps } from 'antd';
import { useRef, useState } from 'react';
import { ProTable, SearchForm, type ProTableRef } from '@bluewhale/whale-ui';
import { pageUsage, type UsageVO } from '../../api/assistant';

type UsageQuery = { assistantId?: string; startDate?: string; endDate?: string };

const { RangePicker } = DatePicker;

export default function UsageBoard() {
  const { message } = App.useApp();
  const tableRef = useRef<ProTableRef>(null);

  const [assistantId, setAssistantId] = useState<string | undefined>(undefined);
  const [startDate, setStartDate] = useState<string | undefined>(undefined);
  const [endDate, setEndDate] = useState<string | undefined>(undefined);

  const columns: TableProps<UsageVO>['columns'] = [
    { title: '日期', dataIndex: 'statDate', width: 120 },
    { title: '助手', dataIndex: 'assistantName', width: 160, ellipsis: true },
    {
      title: '编码',
      dataIndex: 'assistantCode',
      width: 160,
      ellipsis: true,
      render: (v?: string) => v || '-',
    },
    { title: '新增会话', dataIndex: 'sessionCount', width: 100, align: 'center' },
    { title: '消息数', dataIndex: 'messageCount', width: 100, align: 'center' },
    {
      title: 'Token（completion）',
      dataIndex: 'completionTokens',
      width: 160,
      align: 'center',
      render: (v: number) => <Tag>{v.toLocaleString()}</Tag>,
    },
  ];

  return (
    <>
      <div style={{ marginBottom: 8, color: '#999', fontSize: 12 }}>
        统计口径：助手×天聚合（Asia/Shanghai）；token 为 whale-ai 估算值
      </div>
      <ProTable<UsageVO, UsageQuery>
        ref={tableRef}
        rowKey="statDate"
        title="AI 助手用量看板"
        columns={columns}
        request={pageUsage}
        params={{ assistantId, startDate, endDate }}
        onError={(error) =>
          message.error(error instanceof Error ? error.message : '用量看板加载失败')
        }
        search={
        <SearchForm
          items={[
            {
              name: 'assistantId',
              label: '助手ID',
              element: <Input allowClear placeholder="助手ID（数字）" style={{ width: 160 }} />,
            },
            {
              name: 'range',
              label: '日期范围',
              element: (
                <RangePicker
                  allowEmpty={[true, true]}
                  onChange={(dates) => {
                    setStartDate(dates?.[0] ? dates[0].format('YYYY-MM-DD') : undefined);
                    setEndDate(dates?.[1] ? dates[1].format('YYYY-MM-DD') : undefined);
                    tableRef.current?.reload();
                  }}
                />
              ),
            },
          ]}
          onSearch={(values) => {
            setAssistantId((values.assistantId as string) || undefined);
            tableRef.current?.reload();
          }}
          onReset={() => {
            setAssistantId(undefined);
            setStartDate(undefined);
            setEndDate(undefined);
          }}
        />
      }
    />
    </>
  );
}
