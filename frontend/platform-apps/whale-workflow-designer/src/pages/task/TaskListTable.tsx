// 待办列表区：状态过滤 + 受控分页表格，行操作打开审批抽屉。

import { Button, Card, Select, Space, Table, Tag, Typography, type TableColumnsType } from 'antd';
import { useMemo } from 'react';
import type { ProcessTask } from '../../api/workflow';
import { STATUS_COLOR, STATUS_OPTIONS, STATUS_TEXT } from './constants';
import type { TaskListController } from './useTaskList';

interface TaskListTableProps {
  list: TaskListController;
  /** 打开审批（待办）/ 查看（非待办）抽屉 */
  onOpenTask: (record: ProcessTask) => void;
}

export default function TaskListTable({ list, onOpenTask }: TaskListTableProps) {
  const { query, setQuery, dataSource, total, loading, instanceMap } = list;

  const columns: TableColumnsType<ProcessTask> = useMemo(
    () => [
      {
        title: '节点名称',
        dataIndex: 'nodeName',
        width: 160,
        render: (v: string | null) => v || '-',
      },
      {
        title: '审批人',
        dataIndex: 'assignee',
        width: 120,
        render: (v: string | null) => v || '-',
      },
      {
        title: '所属实例',
        key: 'instance',
        render: (_, record) => {
          const brief = instanceMap[record.instanceId];
          if (!brief) {
            return '-';
          }
          return (
            <Space direction="vertical" size={0}>
              <span>{brief.definitionCode}</span>
              <Typography.Text type="secondary">{brief.title}</Typography.Text>
            </Space>
          );
        },
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 100,
        render: (value: number | undefined) => (
          <Tag color={STATUS_COLOR[value ?? -1] ?? 'default'}>
            {STATUS_TEXT[value ?? -1] ?? '-'}
          </Tag>
        ),
      },
      {
        title: '创建时间',
        dataIndex: 'createTime',
        width: 180,
        render: (v: string | undefined) => v || '-',
      },
      {
        title: '操作',
        key: 'action',
        width: 100,
        render: (_, record) => (
          <Button type="link" size="small" onClick={() => onOpenTask(record)}>
            {record.status === 0 ? '审批' : '查看'}
          </Button>
        ),
      },
    ],
    [instanceMap, onOpenTask],
  );

  return (
    <>
      <Card size="small" style={{ marginBottom: 12 }}>
        <Space>
          <span>状态</span>
          <Select
            value={query.status}
            allowClear
            placeholder="全部"
            options={STATUS_OPTIONS}
            style={{ width: 140 }}
            onChange={(value: number | undefined) =>
              setQuery((prev) => ({ ...prev, status: value, current: 1 }))
            }
          />
        </Space>
      </Card>

      <Table<ProcessTask>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={dataSource}
        onChange={(pagination) => {
          const nextSize = pagination.pageSize ?? query.size;
          setQuery((prev) =>
            nextSize !== prev.size
              ? { ...prev, size: nextSize, current: 1 }
              : { ...prev, current: pagination.current ?? 1 },
          );
        }}
        pagination={{
          current: query.current,
          pageSize: query.size,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
        }}
      />
    </>
  );
}
