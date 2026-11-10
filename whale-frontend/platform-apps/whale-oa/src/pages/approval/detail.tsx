// 审批详情抽屉：流程时间线、业务白名单详情与审批动作（同意/驳回/转办/加签/退回/催办/撤销）
//
// 动作按钮仅在 Workflow 返回 canOperate / canCancel / canUrge 时展示；
// monitor 模式全部只读。

import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Input,
  Modal,
  Space,
  Spin,
  Tag,
  Timeline,
  message,
} from 'antd';
import type { ApprovalDetail, ApprovalTimelineTask } from '../../api/approval';
import {
  addSign,
  approveTask,
  cancelInstance,
  getDetail,
  getMonitorDetail,
  rejectTask,
  rollbackTask,
  transferTask,
  urgeInstance,
} from '../../api/approval';

/** 动作类型 */
type ActionKind = 'approve' | 'reject' | 'transfer' | 'add-sign' | 'rollback' | 'urge';

/** 动作弹窗标题 */
const ACTION_TITLES: Record<ActionKind, string> = {
  approve: '同意审批',
  reject: '驳回审批',
  transfer: '转办任务',
  'add-sign': '加签',
  rollback: '退回任务',
  urge: '催办',
};

/** 实例状态文案 */
const INSTANCE_STATUS: Record<number, string> = {
  0: '运行中',
  1: '已通过',
  2: '已驳回',
  3: '已撤销',
};

/** 任务状态文案 */
const TASK_STATUS: Record<number, { text: string; color: string }> = {
  0: { text: '待办', color: 'processing' },
  1: { text: '同意', color: 'success' },
  2: { text: '驳回', color: 'error' },
  3: { text: '取消', color: 'default' },
};

/** 本地审批状态文案 */
const LOCAL_STATUS: Record<string, string> = {
  RUNNING: '运行中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  CANCELLED: '已撤销',
  BROKEN: '异常',
};

/** 时间展示 */
function formatTime(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '-';
}

/** 状态标签 */
function StatusTag({ status, statusName }: { status?: number; statusName?: string }) {
  const text = statusName ?? (status === undefined ? '-' : INSTANCE_STATUS[status] ?? String(status));
  const color = status === 1 ? 'success' : status === 2 ? 'error' : status === 3 ? 'default' : 'processing';
  return <Tag color={color}>{text}</Tag>;
}

interface ApprovalDetailDrawerProps {
  workflowInstanceId: number | null;
  open: boolean;
  monitor?: boolean;
  onClose: () => void;
  onChanged?: () => void;
}

export default function ApprovalDetailDrawer({
  workflowInstanceId,
  open,
  monitor = false,
  onClose,
  onChanged,
}: ApprovalDetailDrawerProps) {
  const [detail, setDetail] = useState<ApprovalDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [action, setAction] = useState<{ kind: ActionKind; task?: ApprovalTimelineTask } | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [comment, setComment] = useState('');
  const [targetUser, setTargetUser] = useState('');
  const [assignees, setAssignees] = useState('');
  const [targetNodeKey, setTargetNodeKey] = useState('');

  const load = useCallback(async () => {
    if (!workflowInstanceId) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const data = monitor
        ? await getMonitorDetail(workflowInstanceId)
        : await getDetail(workflowInstanceId);
      setDetail(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载审批详情失败');
    } finally {
      setLoading(false);
    }
  }, [monitor, workflowInstanceId]);

  useEffect(() => {
    if (open && workflowInstanceId) {
      setDetail(null);
      void load();
    }
  }, [load, open, workflowInstanceId]);

  /** 打开动作弹窗并重置表单 */
  const openAction = (kind: ActionKind, task?: ApprovalTimelineTask) => {
    setComment('');
    setTargetUser('');
    setAssignees('');
    setTargetNodeKey(detail?.instance.currentNodeKey ?? '');
    setAction({ kind, task });
  };

  /** 提交动作 */
  const submitAction = async () => {
    if (!action || !detail) {
      return;
    }
    const instanceId = detail.instance.workflowInstanceId;
    setSubmitting(true);
    try {
      if (action.kind === 'urge') {
        await urgeInstance(instanceId, targetUser || undefined, comment || undefined);
      } else if (action.kind === 'transfer') {
        if (!targetUser.trim()) {
          message.warning('请输入转办目标账号');
          return;
        }
        await transferTask(instanceId, action.task!.id, targetUser.trim());
      } else if (action.kind === 'add-sign') {
        const list = assignees.split(',').map((item) => item.trim()).filter(Boolean);
        if (list.length === 0) {
          message.warning('请输入加签账号，多个账号用逗号分隔');
          return;
        }
        await addSign(instanceId, action.task!.id, list);
      } else if (action.kind === 'rollback') {
        if (!targetNodeKey.trim()) {
          message.warning('请输入退回节点键');
          return;
        }
        await rollbackTask(instanceId, action.task!.id, targetNodeKey.trim());
      } else if (action.kind === 'approve') {
        await approveTask(instanceId, action.task!.id, comment || undefined);
      } else {
        await rejectTask(instanceId, action.task!.id, comment || undefined);
      }
      message.success(`${ACTION_TITLES[action.kind]}成功`);
      setAction(null);
      await load();
      onChanged?.();
    } catch (err) {
      message.error(err instanceof Error ? err.message : `${ACTION_TITLES[action.kind]}失败`);
    } finally {
      setSubmitting(false);
    }
  };

  /** 撤销当前流程 */
  const submitCancel = () => {
    if (!detail) {
      return;
    }
    Modal.confirm({
      title: '撤销审批',
      content: '撤销后流程将终止，确定继续吗？',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await cancelInstance(detail.instance.workflowInstanceId);
          message.success('审批已撤销');
          await load();
          onChanged?.();
        } catch (err) {
          message.error(err instanceof Error ? err.message : '撤销失败');
        }
      },
    });
  };

  const operableTasks = (detail?.tasks ?? []).filter((task) => task.canOperate);
  const primaryTask = operableTasks[0];

  return (
    <Drawer
      title={detail?.instance.title ?? '审批详情'}
      width={760}
      open={open}
      onClose={onClose}
      destroyOnClose
    >
      {loading ? (
        <Spin />
      ) : error ? (
        <Alert type="error" message={error} showIcon />
      ) : detail ? (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Descriptions column={2} size="small" bordered>
            <Descriptions.Item label="审批状态">
              <StatusTag status={detail.instance.status} statusName={detail.instance.statusName} />
            </Descriptions.Item>
            <Descriptions.Item label="流程定义">{detail.instance.definitionCode ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="发起人">{detail.instance.initiator ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="当前节点">{detail.instance.currentNodeKey ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="发起时间">{formatTime(detail.instance.createTime)}</Descriptions.Item>
            <Descriptions.Item label="更新时间">{formatTime(detail.instance.updateTime)}</Descriptions.Item>
            {detail.instance.businessCode ? (
              <Descriptions.Item label="业务单号">{detail.instance.businessCode}</Descriptions.Item>
            ) : null}
            {detail.instance.businessType ? (
              <Descriptions.Item label="业务类型">{detail.instance.businessType}</Descriptions.Item>
            ) : null}
          </Descriptions>

          {detail.businessDetail ? (
            <Card size="small" title="业务信息">
              {detail.businessDetail.summary ? (
                <div style={{ marginBottom: 8, fontWeight: 500 }}>{detail.businessDetail.summary}</div>
              ) : null}
              <Descriptions column={2} size="small">
                {(detail.businessDetail.fields ?? []).map((field, index) => (
                  <Descriptions.Item key={`${field.label}-${index}`} label={field.label}>
                    {field.value || '-'}
                  </Descriptions.Item>
                ))}
              </Descriptions>
            </Card>
          ) : null}

          {!monitor && (primaryTask || detail.canUrge || detail.canCancel) ? (
            <Card size="small" title="审批操作">
              <Space wrap>
                {primaryTask ? (
                  <>
                    <Button type="primary" onClick={() => openAction('approve', primaryTask)}>
                      同意
                    </Button>
                    <Button danger onClick={() => openAction('reject', primaryTask)}>
                      驳回
                    </Button>
                    <Button onClick={() => openAction('transfer', primaryTask)}>转办</Button>
                    <Button onClick={() => openAction('add-sign', primaryTask)}>加签</Button>
                    <Button onClick={() => openAction('rollback', primaryTask)}>退回</Button>
                  </>
                ) : null}
                {detail.canUrge ? <Button onClick={() => openAction('urge')}>催办</Button> : null}
                {detail.canCancel ? (
                  <Button danger onClick={submitCancel}>
                    撤销
                  </Button>
                ) : null}
              </Space>
            </Card>
          ) : null}

          <Card size="small" title="审批轨迹">
            {(detail.tasks ?? []).length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无任务记录" />
            ) : (
              <Timeline
                items={(detail.tasks ?? []).map((task) => {
                  const status = task.status === undefined ? undefined : TASK_STATUS[task.status];
                  return {
                    color: status?.color,
                    children: (
                      <Space direction="vertical" size={2}>
                        <Space>
                          <strong>{task.nodeName ?? task.nodeKey ?? '审批节点'}</strong>
                          {status ? <Tag color={status.color}>{status.text}</Tag> : null}
                        </Space>
                        <span>审批人：{task.assignee ?? '-'}</span>
                        {task.ownerId && task.ownerId !== task.assignee ? (
                          <span>任务归属：{task.ownerId}</span>
                        ) : null}
                        {task.delegateFrom ? <span>委派来源：{task.delegateFrom}</span> : null}
                        {task.comment ? <span>意见：{task.comment}</span> : null}
                        <span>
                          创建：{formatTime(task.createTime)}，更新：{formatTime(task.updateTime)}
                        </span>
                      </Space>
                    ),
                  };
                })}
              />
            )}
          </Card>

          {(detail.ccs ?? []).length > 0 ? (
            <Card size="small" title="抄送记录">
              <Space direction="vertical" size={4}>
                {(detail.ccs ?? []).map((cc) => (
                  <span key={cc.id}>
                    {cc.nodeName ?? cc.nodeKey ?? '抄送'}：{cc.readFlag === 1 ? '已读' : '未读'}
                    {cc.readTime ? `（${formatTime(cc.readTime)}）` : ''}
                  </span>
                ))}
              </Space>
            </Card>
          ) : (
            <span style={{ color: '#999' }}>
              业务状态：
              {detail.businessDetail?.status
                ? LOCAL_STATUS[detail.businessDetail.status] ?? detail.businessDetail.status
                : '-'}
            </span>
          )}

          {detail.instance.businessUrl ? (
            <Button
              type="link"
              onClick={() => {
                message.info(`业务详情路由：${detail.instance.businessUrl}`);
              }}
            >
              查看业务单据：{detail.instance.businessUrl}
            </Button>
          ) : null}
        </Space>
      ) : null}

      <Modal
        title={action ? ACTION_TITLES[action.kind] : ''}
        open={action !== null}
        confirmLoading={submitting}
        onOk={submitAction}
        onCancel={() => setAction(null)}
        destroyOnClose
      >
        {action?.kind === 'transfer' ? (
          <Input
            placeholder="转办目标账号"
            value={targetUser}
            onChange={(event) => setTargetUser(event.target.value)}
          />
        ) : null}
        {action?.kind === 'add-sign' ? (
          <Input
            placeholder="加签账号，多个账号用逗号分隔"
            value={assignees}
            onChange={(event) => setAssignees(event.target.value)}
          />
        ) : null}
        {action?.kind === 'rollback' ? (
          <Input
            placeholder="退回目标节点键"
            value={targetNodeKey}
            onChange={(event) => setTargetNodeKey(event.target.value)}
          />
        ) : null}
        {action?.kind === 'urge' ? (
          <Space direction="vertical" style={{ width: '100%' }}>
            <Input
              placeholder="被催办人账号（留空则催办全部待办人）"
              value={targetUser}
              onChange={(event) => setTargetUser(event.target.value)}
            />
            <Input.TextArea
              rows={3}
              placeholder="催办说明（可选）"
              value={comment}
              onChange={(event) => setComment(event.target.value)}
            />
          </Space>
        ) : null}
        {action?.kind === 'approve' || action?.kind === 'reject' ? (
          <Input.TextArea
            rows={3}
            placeholder="审批意见（可选）"
            value={comment}
            onChange={(event) => setComment(event.target.value)}
          />
        ) : null}
      </Modal>
    </Drawer>
  );
}
