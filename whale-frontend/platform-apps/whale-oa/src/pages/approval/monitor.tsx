// 审批流程监控：只读查看全部流程实例与任务轨迹（需 sys:oa:approval:monitor）

import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Empty, Input, Select, Space, Table, Tag, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import ApprovalDetailDrawer from './detail';
import { pageMonitor } from '../../api/approval';
import type { ApprovalInstanceRow } from '../../api/approval';

/** 实例状态文案 */
const INSTANCE_STATUS: Record<number, string> = {
  0: '运行中',
  1: '已通过',
  2: '已驳回',
  3: '已撤销',
};

/** 时间展示 */
function formatTime(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '-';
}

export default function ApprovalMonitor() {
  const [records, setRecords] = useState<ApprovalInstanceRow[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<number | undefined>();
  const [definitionCode, setDefinitionCode] = useState<string>('');
  const [loading, setLoading] = useState(false);
  const [detailId, setDetailId] = useState<number | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageMonitor({
        current,
        size,
        keyword: keyword.trim() || undefined,
        status,
        definitionCode: definitionCode.trim() || undefined,
      });
      setRecords(result.records);
      setTotal(result.total);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '加载流程监控失败');
    } finally {
      setLoading(false);
    }
  }, [current, definitionCode, keyword, size, status]);

  useEffect(() => {
    void load();
  }, [load]);

  const columns: ColumnsType<ApprovalInstanceRow> = [
    {
      title: '流程实例ID',
      dataIndex: 'workflowInstanceId',
      width: 110,
    },
    {
      title: '标题',
      dataIndex: 'title',
      ellipsis: true,
      render: (value: string | undefined) => value || '-',
    },
    {
      title: '流程定义',
      dataIndex: 'definitionCode',
      width: 150,
      render: (value: string | undefined) => value || '-',
    },
    {
      title: '发起人',
      dataIndex: 'initiator',
      width: 110,
      render: (value: string | undefined) => value || '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 95,
      render: (value: number | undefined, row) => (
        <Tag
          color={
            value === 1 ? 'success' : value === 2 ? 'error' : value === 3 ? 'default' : 'processing'
          }
        >
          {row.statusName ?? (value === undefined ? '-' : (INSTANCE_STATUS[value] ?? value))}
        </Tag>
      ),
    },
    {
      title: '当前节点',
      dataIndex: 'currentNodeKey',
      width: 130,
      render: (value: string | undefined) => value || '-',
    },
    {
      title: '发起时间',
      dataIndex: 'createTime',
      width: 170,
      render: (value: string | undefined) => formatTime(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 80,
      render: (_, row) => (
        <Button
          type="link"
          onClick={() => {
            setDetailId(row.workflowInstanceId);
            setDetailOpen(true);
          }}
        >
          查看
        </Button>
      ),
    },
  ];

  return (
    <Card
      title="审批流程监控"
      extra={
        <Space>
          <Input.Search
            allowClear
            placeholder="搜索标题或业务单号"
            style={{ width: 220 }}
            onSearch={(value) => {
              setKeyword(value);
              setCurrent(1);
            }}
          />
          <Select
            allowClear
            placeholder="实例状态"
            style={{ width: 130 }}
            value={status}
            onChange={(value) => {
              setStatus(value);
              setCurrent(1);
            }}
            options={[
              { value: 0, label: '运行中' },
              { value: 1, label: '已通过' },
              { value: 2, label: '已驳回' },
              { value: 3, label: '已撤销' },
            ]}
          />
          <Input
            allowClear
            placeholder="流程定义编码"
            style={{ width: 170 }}
            value={definitionCode}
            onChange={(event) => {
              setDefinitionCode(event.target.value);
              setCurrent(1);
            }}
          />
          <Button onClick={() => void load()}>刷新</Button>
        </Space>
      }
    >
      <Table<ApprovalInstanceRow>
        rowKey="workflowInstanceId"
        loading={loading}
        columns={columns}
        dataSource={records}
        locale={{
          emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无流程实例" />,
        }}
        pagination={{
          current,
          pageSize: size,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (page, pageSize) => {
            setCurrent(page);
            setSize(pageSize);
          },
        }}
      />
      <ApprovalDetailDrawer
        workflowInstanceId={detailId}
        open={detailOpen}
        monitor
        onClose={() => setDetailOpen(false)}
      />
    </Card>
  );
}
