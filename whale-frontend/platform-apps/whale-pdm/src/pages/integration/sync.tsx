// 集成同步运维：查看各通道同步日志，失败记录可重试
import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Input, Select, Space, Table, Tag, Typography, message } from 'antd';
import type { TableProps } from 'antd';
import {
  pageSyncLogs,
  retrySync,
  type SyncChannel,
  type SyncLog,
  type SyncStatus,
} from '../../api/integration';
import { hasPermission } from '../../utils/permission';

/** 通道选项 */
const CHANNEL_OPTIONS: { label: string; value: SyncChannel }[] = [
  { label: 'CAD', value: 'CAD' },
  { label: 'ERP', value: 'ERP' },
  { label: 'MES', value: 'MES' },
  { label: 'OA', value: 'OA' },
];

/** 状态文案与颜色 */
const STATUS_META: Record<SyncStatus, { text: string; color: string }> = {
  SUCCESS: { text: '成功', color: 'green' },
  FAILED: { text: '失败', color: 'red' },
};

/** 支持手动重试的动作 */
const RETRYABLE_ACTIONS = new Set(['PART_UPSERT', 'ROUTE_PUSH', 'BOM_NOTIFY']);

const { Text } = Typography;

export default function SyncCenter() {
  const [logs, setLogs] = useState<SyncLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(20);
  const [channel, setChannel] = useState<SyncChannel | undefined>();
  const [status, setStatus] = useState<SyncStatus | undefined>();
  const [bizNo, setBizNo] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageSyncLogs({
        current,
        size,
        channel,
        status,
        bizNo: bizNo || undefined,
      });
      setLogs(page.records);
      setTotal(Number(page.total));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '同步日志加载失败');
    } finally {
      setLoading(false);
    }
  }, [bizNo, channel, current, size, status]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleRetry = async (log: SyncLog) => {
    try {
      await retrySync(log.id);
      message.success('已重新执行同步');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '重试失败');
    }
  };

  const columns: TableProps<SyncLog>['columns'] = [
    { title: '通道', dataIndex: 'channel', width: 80 },
    { title: '动作', dataIndex: 'action', width: 150 },
    {
      title: '业务编号',
      dataIndex: 'bizNo',
      width: 180,
      render: (value?: string, record?: SyncLog) => value || `#${record?.bizId ?? '-'}`,
    },
    { title: '版本', dataIndex: 'versionNo', width: 90, render: (value?: string) => value || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: SyncStatus) => {
        const meta = STATUS_META[value];
        return <Tag color={meta?.color ?? 'default'}>{meta?.text ?? value}</Tag>;
      },
    },
    { title: '重试', dataIndex: 'retryCount', width: 70 },
    {
      title: '失败原因',
      dataIndex: 'lastError',
      render: (value?: string) =>
        value ? (
          <Text type="danger" ellipsis={{ tooltip: value }}>
            {value}
          </Text>
        ) : (
          '-'
        ),
    },
    {
      title: '完成时间',
      dataIndex: 'finishedAt',
      width: 170,
      render: (value?: string) => value || '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 90,
      render: (_, record) =>
        record.status === 'FAILED' &&
        RETRYABLE_ACTIONS.has(record.action) &&
        hasPermission('sys:pdm:int:sync:retry') ? (
          <Button type="link" size="small" onClick={() => void handleRetry(record)}>
            重试
          </Button>
        ) : (
          '-'
        ),
    },
  ];

  return (
    <Card title="集成同步记录">
      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          allowClear
          placeholder="通道"
          style={{ width: 120 }}
          options={CHANNEL_OPTIONS}
          value={channel}
          onChange={(value) => {
            setChannel(value);
            setCurrent(1);
          }}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 120 }}
          options={[
            { label: '成功', value: 'SUCCESS' },
            { label: '失败', value: 'FAILED' },
          ]}
          value={status}
          onChange={(value) => {
            setStatus(value);
            setCurrent(1);
          }}
        />
        <Input
          allowClear
          placeholder="业务编号"
          style={{ width: 200 }}
          value={bizNo}
          onChange={(event) => {
            setBizNo(event.target.value);
            setCurrent(1);
          }}
        />
        <Button onClick={() => void load()}>刷新</Button>
      </Space>
      <Table<SyncLog>
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={logs}
        pagination={{
          current,
          pageSize: size,
          total,
          showSizeChanger: true,
          onChange: (page, pageSize) => {
            setCurrent(page);
            setSize(pageSize);
          },
        }}
      />
    </Card>
  );
}
