// 审计日志查询：分页表 + 模块/URI 关键字/方法筛选

import { useState } from 'react';
import { App, Input, Select, Tag, type TableProps } from 'antd';
import { ProTable, SearchForm } from '@bluewhale/whale-ui';
import { pageAuditLog, type AuditLogQuery, type AuditLogVO } from '../api/audit';

const METHOD_OPTIONS = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'].map((value) => ({ value, label: value }));

const columns: TableProps<AuditLogVO>['columns'] = [
  { title: '时间', dataIndex: 'createTime', width: 180, render: (value?: string) => value ?? '-' },
  { title: '模块', dataIndex: 'module', width: 140, render: (value?: string) => value ?? '-' },
  { title: '请求 URI', dataIndex: 'requestUri', render: (value?: string) => value ?? '-' },
  { title: '方法', dataIndex: 'httpMethod', width: 90, render: (value?: string) => value ?? '-' },
  { title: '响应码', dataIndex: 'responseCode', width: 90, render: (value?: number) => value ?? '-' },
  {
    title: '业务状态',
    dataIndex: 'bizStatus',
    width: 100,
    render: (value?: number) =>
      value === undefined ? '-' : <Tag color={value === 1 ? 'success' : 'error'}>{value === 1 ? '成功' : '失败'}</Tag>,
  },
  { title: '耗时 ms', dataIndex: 'costMs', width: 90, render: (value?: number) => value ?? '-' },
  { title: 'IP', dataIndex: 'ip', width: 140, render: (value?: string) => value ?? '-' },
];

export default function Audit() {
  const { message } = App.useApp();
  const [query, setQuery] = useState<AuditLogQuery>({});

  return (
    <ProTable<AuditLogVO, AuditLogQuery>
      rowKey="id"
      title="审计日志"
      columns={columns}
      request={pageAuditLog}
      params={query}
      onError={(error) => message.error(error instanceof Error ? error.message : '审计日志加载失败')}
      search={
        <SearchForm
          items={[
            { name: 'module', label: '模块', element: <Input allowClear placeholder="如 whale-auth" /> },
            { name: 'requestUri', label: 'URI 关键字', element: <Input allowClear placeholder="如 /auth/user" /> },
            {
              name: 'httpMethod',
              label: '方法',
              element: <Select allowClear options={METHOD_OPTIONS} placeholder="全部" style={{ width: 120 }} />,
            },
          ]}
          onSearch={(values) =>
            setQuery({
              module: (values.module as string) || undefined,
              requestUri: (values.requestUri as string) || undefined,
              httpMethod: (values.httpMethod as string) || undefined,
            })
          }
          onReset={() => setQuery({})}
        />
      }
    />
  );
}
