// 我的待办：antd Table 展示 pageMyTask() 结果（任务名 / 流程实例 / 节点 / 创建时间 / 操作）
import { useCallback, useEffect, useState } from 'react';
import { Button, Space, Table, Tag } from 'antd';
import type { TableProps } from 'antd';
import { createLogger } from '@bluewhale/whale-common-tools';
import { pageMyTask, type WorkflowTask } from '../api/workflow';

const logger = createLogger('whale-portal › todo');

/** 任务状态文案 */
const STATUS_LABEL: Record<number, string> = {
  0: '待办',
  1: '已同意',
  2: '已驳回',
  3: '已取消',
};

/** 任务状态颜色 */
const STATUS_COLOR: Record<number, string> = {
  0: 'processing',
  1: 'success',
  2: 'error',
  3: 'default',
};

export default function Todo() {
  const [data, setData] = useState<WorkflowTask[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await pageMyTask({ pageSize: 50 }));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const columns: TableProps<WorkflowTask>['columns'] = [
    { title: '任务名', dataIndex: 'nodeName', key: 'nodeName' },
    { title: '流程实例', dataIndex: 'instanceId', key: 'instanceId' },
    { title: '节点', dataIndex: 'nodeKey', key: 'nodeKey' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (value: number) => (
        <Tag color={STATUS_COLOR[value] || 'default'}>{STATUS_LABEL[value] || value}</Tag>
      ),
    },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime' },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: () => (
        // TODO: 接入任务审批页（跳转 /workflow/task/{id}），当前为占位
        <Button type="link" onClick={() => logger.warn('「去处理」为占位实现')}>
          去处理
        </Button>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Button onClick={() => void load()}>刷新</Button>
      <Table<WorkflowTask> rowKey="id" loading={loading} columns={columns} dataSource={data} />
    </Space>
  );
}
