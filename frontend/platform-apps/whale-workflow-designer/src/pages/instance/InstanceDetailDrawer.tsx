// 流程实例详情抽屉（F5 子组件）：基本信息（title/编码/版本/状态/当前节点/发起人/业务键/时间）
// + 任务流转轨迹（tasks[] 按 nodeIndex 升序，纵向 Steps 展示 nodeName/assignee/状态/意见/时间）。
// 契约层 ../../api/workflow 只读不改；实例 ID 全程 string（禁止 Number(id)）。

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  App,
  Descriptions,
  Drawer,
  Space,
  Spin,
  Steps,
  Typography,
  type DescriptionsProps,
} from 'antd';
import { workflowApi, type Id, type ProcessInstance, type ProcessTask } from '../../api/workflow';
import { InstanceStatusTag, TaskStatusTag } from './statusTag';

const { Text } = Typography;

interface InstanceDetailDrawerProps {
  open: boolean;
  /** 实例 ID（雪花 ID，字符串承载）；为 null 时不加载 */
  instanceId: Id | null;
  onClose: () => void;
}

/** 任务状态 → antd Steps 步骤状态 */
function toStepStatus(status?: number): 'wait' | 'process' | 'finish' | 'error' {
  switch (status) {
    case 0:
      return 'process';
    case 1:
      return 'finish';
    case 2:
      return 'error';
    default:
      return 'wait';
  }
}

/** 按 nodeIndex 升序排列任务（缺失 nodeIndex 视为 0；同序保持原顺序稳定） */
function sortTasks(tasks: ProcessTask[]): ProcessTask[] {
  return tasks
    .map((task, index) => ({ task, index }))
    .sort((a, b) => (a.task.nodeIndex ?? 0) - (b.task.nodeIndex ?? 0) || a.index - b.index)
    .map((entry) => entry.task);
}

export default function InstanceDetailDrawer({ open, instanceId, onClose }: InstanceDetailDrawerProps) {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<ProcessInstance | null>(null);

  const load = useCallback(
    async (id: Id) => {
      setLoading(true);
      try {
        setDetail(await workflowApi.instanceDetail(id));
      } catch (e) {
        setDetail(null);
        message.error((e as Error).message);
      } finally {
        setLoading(false);
      }
    },
    [message],
  );

  // 打开且实例 ID 有效时拉取详情；关闭时清空，避免残留上一次数据
  useEffect(() => {
    if (open && instanceId !== null) {
      void load(instanceId);
    } else {
      setDetail(null);
    }
  }, [open, instanceId, load]);

  const tasks = useMemo(() => sortTasks(detail?.tasks ?? []), [detail]);

  // 当前节点优先映射为节点名（取轨迹中同 nodeKey 任务），拿不到则直接显示 key
  const currentNodeLabel = useMemo(() => {
    const key = detail?.currentNodeKey;
    if (!key) {
      return '-';
    }
    const matched = tasks.find((task) => task.nodeKey === key);
    return matched?.nodeName ? `${matched.nodeName}（${key}）` : key;
  }, [detail, tasks]);

  const basicItems: DescriptionsProps['items'] = [
    { key: 'id', label: '实例 ID', children: detail?.id ?? '-' },
    { key: 'title', label: '标题', children: detail?.title ?? '-' },
    { key: 'definitionCode', label: '流程编码', children: detail?.definitionCode ?? '-' },
    { key: 'definitionVersion', label: '版本', children: detail?.definitionVersion ?? '-' },
    {
      key: 'status',
      label: '状态',
      children: detail ? <InstanceStatusTag status={detail.status} /> : '-',
    },
    { key: 'currentNode', label: '当前节点', children: currentNodeLabel },
    { key: 'initiator', label: '发起人', children: detail?.initiator ?? '-' },
    { key: 'businessKey', label: '业务键', children: detail?.businessKey || '-' },
    { key: 'createTime', label: '创建时间', children: detail?.createTime ?? '-' },
    { key: 'updateTime', label: '更新时间', children: detail?.updateTime ?? '-' },
  ];

  const stepItems = tasks.map((task, index) => ({
    title: (
      <Space size={6}>
        <Text strong>{task.nodeName || task.nodeKey || `节点 ${index + 1}`}</Text>
        <TaskStatusTag status={task.status} />
      </Space>
    ),
    status: toStepStatus(task.status),
    description: (
      <Space direction="vertical" size={2}>
        <Text type="secondary">审批人：{task.assignee || '-'}</Text>
        {task.comment ? <Text type="secondary">意见：{task.comment}</Text> : null}
        <Text type="secondary">时间：{task.createTime || '-'}</Text>
      </Space>
    ),
  }));

  return (
    <Drawer title="流程实例详情" width={640} open={open} onClose={onClose}>
      <Spin spinning={loading}>
        <Descriptions column={2} bordered size="small" items={basicItems} />

        <div style={{ marginTop: 24, marginBottom: 12 }}>
          <Text strong>流转轨迹</Text>
        </div>
        {tasks.length === 0 ? (
          <Text type="secondary">暂无任务轨迹</Text>
        ) : (
          <Steps direction="vertical" size="small" items={stepItems} />
        )}
      </Spin>
    </Drawer>
  );
}
