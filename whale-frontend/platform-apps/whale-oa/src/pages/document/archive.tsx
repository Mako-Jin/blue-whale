// OA 公文 - 归档管理：归档映射查询与失败重试
import { useCallback, useEffect, useState } from 'react';
import { App, Button, Input, Popconfirm, Select, Space, Table, Tag } from 'antd';
import type { TableProps } from 'antd';
import {
  pageArchiveRefs,
  retryArchiveRef,
  type OaArchiveStatus,
  type OaDocArchiveRef,
  type OaDocType,
} from '../../api/document';
import { hasPermission } from '../../utils/permission';

/** 归档状态文案 */
const STATUS_META: Record<OaArchiveStatus, { text: string; color: string }> = {
  PENDING: { text: '待推送', color: 'processing' },
  ARCHIVED: { text: '已归档', color: 'success' },
  FAILED: { text: '推送失败', color: 'error' },
};

/** 单据类型选项 */
const DOC_TYPE_OPTIONS = [
  { value: 'OUTGOING', label: '发文' },
  { value: 'INCOMING', label: '收文' },
];

/** 时间展示 */
function timeText(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}

/** 单据类型展示 */
function docTypeText(value?: string): string {
  return DOC_TYPE_OPTIONS.find((item) => item.value === value)?.label ?? value ?? '-';
}

export default function DocumentArchivePage() {
  const { message } = App.useApp();
  const [data, setData] = useState<OaDocArchiveRef[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<OaArchiveStatus | ''>('');
  const [docType, setDocType] = useState<OaDocType | ''>('');
  const [loading, setLoading] = useState(false);

  const canArchive = hasPermission('sys:oa:document:archive');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageArchiveRefs({
        current,
        size: 10,
        keyword: keyword.trim() || undefined,
        status: status || undefined,
        docType: docType || undefined,
      });
      setData(page.records);
      setTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '归档记录查询失败');
      setData([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [current, keyword, status, docType, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleRetry = async (record: OaDocArchiveRef) => {
    try {
      await retryArchiveRef(record.id);
      message.success('归档已重试成功');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '归档重试失败');
      await load();
    }
  };

  const columns: TableProps<OaDocArchiveRef>['columns'] = [
    {
      title: '单据类型',
      dataIndex: 'docType',
      key: 'docType',
      width: 100,
      render: (value?: string) => docTypeText(value),
    },
    {
      title: '文号',
      dataIndex: 'docNo',
      key: 'docNo',
      width: 170,
      render: (value?: string) => value || '-',
    },
    { title: '标题', dataIndex: 'docTitle', key: 'docTitle', ellipsis: true },
    { title: '来源ID', dataIndex: 'sourceId', key: 'sourceId', width: 130 },
    {
      title: '档案条目',
      dataIndex: 'archiveItemId',
      key: 'archiveItemId',
      width: 140,
      render: (value?: string) => value || '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: OaArchiveStatus) => {
        const meta = STATUS_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '失败原因',
      dataIndex: 'errorMessage',
      key: 'errorMessage',
      width: 220,
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: '推送时间',
      dataIndex: 'pushedAt',
      key: 'pushedAt',
      width: 160,
      render: (value?: string) => timeText(value),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 160,
      render: (value?: string) => timeText(value),
    },
    {
      title: '操作',
      key: 'action',
      width: 90,
      render: (_value, record) =>
        record.status === 'FAILED' && canArchive ? (
          <Popconfirm title="确认重试推送归档？" onConfirm={() => void handleRetry(record)}>
            <Button type="link" size="small">
              重试
            </Button>
          </Popconfirm>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input
          allowClear
          placeholder="来源ID/档案条目"
          style={{ width: 200 }}
          value={keyword}
          onChange={(event) => {
            setKeyword(event.target.value);
            setCurrent(1);
          }}
        />
        <Select
          allowClear
          placeholder="单据类型"
          style={{ width: 120 }}
          value={docType || undefined}
          options={DOC_TYPE_OPTIONS}
          onChange={(value) => {
            setDocType((value as OaDocType) ?? '');
            setCurrent(1);
          }}
        />
        <Select
          allowClear
          placeholder="归档状态"
          style={{ width: 130 }}
          value={status || undefined}
          options={Object.entries(STATUS_META).map(([value, meta]) => ({
            label: meta.text,
            value,
          }))}
          onChange={(value) => {
            setStatus((value as OaArchiveStatus) ?? '');
            setCurrent(1);
          }}
        />
        <Button onClick={() => void load()}>刷新</Button>
      </Space>
      <Table<OaDocArchiveRef>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        scroll={{ x: 1400 }}
        pagination={{
          current,
          pageSize: 10,
          total,
          onChange: (page) => setCurrent(page),
        }}
      />
    </Space>
  );
}
