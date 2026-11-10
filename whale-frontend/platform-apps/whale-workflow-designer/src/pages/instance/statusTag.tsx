// 实例 / 任务状态标签（F5 实例监控子组件）：状态码 → 展示文案与颜色
// 状态码口径见 api/workflow.ts 的 ProcessInstance / ProcessTask 注释。

import { Tag } from 'antd';

/** 实例状态：0 运行中 / 1 已通过 / 2 已驳回 / 3 已撤销 */
const INSTANCE_STATUS: Record<number, { text: string; color: string } | undefined> = {
  0: { text: '运行中', color: 'processing' },
  1: { text: '已通过', color: 'success' },
  2: { text: '已驳回', color: 'error' },
  3: { text: '已撤销', color: 'default' },
};

/** 任务状态：0 待办 / 1 已同意 / 2 已驳回 / 3 已取消 */
const TASK_STATUS: Record<number, { text: string; color: string } | undefined> = {
  0: { text: '待办', color: 'processing' },
  1: { text: '已同意', color: 'success' },
  2: { text: '已驳回', color: 'error' },
  3: { text: '已取消', color: 'default' },
};

/** 实例状态标签；未知状态回退灰色「未知」 */
export function InstanceStatusTag({ status }: { status: number }) {
  const meta = INSTANCE_STATUS[status];
  return <Tag color={meta?.color ?? 'default'}>{meta?.text ?? '未知'}</Tag>;
}

/** 任务状态标签；未知/缺失状态回退灰色「未知」 */
export function TaskStatusTag({ status }: { status?: number }) {
  const meta = status === undefined ? undefined : TASK_STATUS[status];
  return <Tag color={meta?.color ?? 'default'}>{meta?.text ?? '未知'}</Tag>;
}
