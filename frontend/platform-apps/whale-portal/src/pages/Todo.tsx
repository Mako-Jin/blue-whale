// 我的待办：antd Table 展示待办分页结果（任务名 / 流程实例 / 节点 / 创建时间 / 操作）
// 「去处理」打开审批抽屉，可填写意见后同意 / 驳回（POST /workflow/task/{id}/approve|reject）
import { useCallback, useEffect, useState } from 'react';
import { App, Button, Descriptions, Drawer, Input, Space, Table, Tag } from 'antd';
import type { TableProps } from 'antd';
import { getCurrentUser } from '../api/client';
import { approveTask, pageMyTaskPage, rejectTask, type WorkflowTask } from '../api/workflow';

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
  const { message } = App.useApp();
  const [data, setData] = useState<WorkflowTask[]>([]);
  const [loading, setLoading] = useState(false);
  const [activeTask, setActiveTask] = useState<WorkflowTask | null>(null);
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageMyTaskPage({
        pageSize: 50,
        status: 0,
        assignee: getCurrentUser()?.username,
      });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  /** 打开审批抽屉并清空上次意见 */
  const openTask = (task: WorkflowTask) => {
    setActiveTask(task);
    setComment(task.comment ?? '');
  };

  /** 提交审批：同意 / 驳回后关闭抽屉并刷新列表 */
  const submit = async (action: 'approve' | 'reject') => {
    if (!activeTask) {
      return;
    }
    setSubmitting(true);
    try {
      if (action === 'approve') {
        await approveTask(activeTask.id, comment);
      } else {
        await rejectTask(activeTask.id, comment);
      }
      message.success(action === 'approve' ? '已同意' : '已驳回');
      setActiveTask(null);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '审批提交失败');
    } finally {
      setSubmitting(false);
    }
  };

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
      render: (_value, record) => (
        <Button type="link" disabled={record.status !== 0} onClick={() => openTask(record)}>
          去处理
        </Button>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Button onClick={() => void load()}>刷新</Button>
      <Table<WorkflowTask> rowKey="id" loading={loading} columns={columns} dataSource={data} />

      <Drawer
        title="待办审批"
        width={520}
        open={activeTask !== null}
        onClose={() => setActiveTask(null)}
        extra={
          <Space>
            <Button onClick={() => setActiveTask(null)}>取消</Button>
            <Button danger loading={submitting} onClick={() => void submit('reject')}>
              驳回
            </Button>
            <Button type="primary" loading={submitting} onClick={() => void submit('approve')}>
              同意
            </Button>
          </Space>
        }
      >
        {activeTask && (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="任务名">{activeTask.nodeName}</Descriptions.Item>
              <Descriptions.Item label="流程实例">{activeTask.instanceId}</Descriptions.Item>
              <Descriptions.Item label="节点键">{activeTask.nodeKey}</Descriptions.Item>
              <Descriptions.Item label="审批人">{activeTask.assignee ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="创建时间">{activeTask.createTime}</Descriptions.Item>
            </Descriptions>
            <Input.TextArea
              rows={4}
              value={comment}
              placeholder="填写审批意见（可选）"
              onChange={(event) => setComment(event.target.value)}
            />
          </Space>
        )}
      </Drawer>
    </Space>
  );
}
