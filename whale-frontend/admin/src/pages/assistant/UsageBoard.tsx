// AI 助手用量看板页 - 助手×天聚合（会话数 / 消息数 / token），按日期倒序
// 权限码：sys:assistant:define(2300)

import { App, DatePicker, Input, Tag, type TableProps } from 'antd';
import { useRef, useState } from 'react';
import { ProTable, SearchForm, type ProTableRef } from '@bluewhale/whale-ui';
import { pageUsage, type UsageVO } from '../../api/assistant';
import { useI18n } from '../../i18n';

type UsageQuery = { assistantId?: string; startDate?: string; endDate?: string };

const { RangePicker } = DatePicker;

export default function UsageBoard() {
  const { message } = App.useApp();
  const { t } = useI18n();
  const tableRef = useRef<ProTableRef>(null);

  const [assistantId, setAssistantId] = useState<string | undefined>(undefined);
  const [startDate, setStartDate] = useState<string | undefined>(undefined);
  const [endDate, setEndDate] = useState<string | undefined>(undefined);

  const columns: TableProps<UsageVO>['columns'] = [
    { title: t('assistant.usage.column.date'), dataIndex: 'statDate', width: 120 },
    {
      title: t('assistant.usage.column.assistant'),
      dataIndex: 'assistantName',
      width: 160,
      ellipsis: true,
    },
    {
      title: t('assistant.usage.column.code'),
      dataIndex: 'assistantCode',
      width: 160,
      ellipsis: true,
      render: (v?: string) => v || '-',
    },
    {
      title: t('assistant.usage.column.sessions'),
      dataIndex: 'sessionCount',
      width: 100,
      align: 'center',
    },
    {
      title: t('assistant.usage.column.messages'),
      dataIndex: 'messageCount',
      width: 100,
      align: 'center',
    },
    {
      title: t('assistant.usage.column.completionTokens'),
      dataIndex: 'completionTokens',
      width: 160,
      align: 'center',
      render: (v: number) => <Tag>{v.toLocaleString()}</Tag>,
    },
  ];

  return (
    <>
      <div style={{ marginBottom: 8, color: '#999', fontSize: 12 }}>
        {t('assistant.usage.note')}
      </div>
      <ProTable<UsageVO, UsageQuery>
        ref={tableRef}
        rowKey="statDate"
        title={t('assistant.usage.title')}
        columns={columns}
        request={pageUsage}
        params={{ assistantId, startDate, endDate }}
        onError={(error) =>
          message.error(
            error instanceof Error ? error.message : t('assistant.usage.msg.loadFailed'),
          )
        }
        search={
          <SearchForm
            items={[
              {
                name: 'assistantId',
                label: t('assistant.usage.search.assistantId'),
                element: (
                  <Input
                    allowClear
                    placeholder={t('assistant.usage.search.assistantIdPlaceholder')}
                    style={{ width: 160 }}
                  />
                ),
              },
              {
                name: 'range',
                label: t('assistant.usage.search.dateRange'),
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
