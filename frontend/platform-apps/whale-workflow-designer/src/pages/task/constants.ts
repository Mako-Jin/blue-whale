// 待办 / 审批页的常量与共享类型：状态文案与配色、查询状态、动作枚举与表单值。

/** 任务状态文案（0 待办 / 1 已同意 / 2 已驳回 / 3 已取消） */
export const STATUS_TEXT: Record<number, string> = {
  0: '待办',
  1: '已同意',
  2: '已驳回',
  3: '已取消',
};

/** 任务状态 Tag 颜色，未识别状态回落 default */
export const STATUS_COLOR: Record<number, string> = {
  0: 'processing',
  1: 'success',
  2: 'error',
  3: 'default',
};

/** 状态过滤下拉项（清空由 allowClear 置 undefined → 查看全部） */
export const STATUS_OPTIONS = [
  { value: 0, label: '待办' },
  { value: 1, label: '已同意' },
  { value: 2, label: '已驳回' },
  { value: 3, label: '已取消' },
];

/** 页面查询状态：受控分页 + 状态过滤（默认只看待办 status=0） */
export interface QueryState {
  current: number;
  size: number;
  status?: number;
}

/** 所属实例摘要：任务行不自带定义编码/标题，按需补查实例详情 */
export interface InstanceBrief {
  definitionCode: string;
  title: string;
}

/** 审批动作 */
export type ApprovalAction = 'approve' | 'reject';

/** 任务办理动作（弹窗收集入参） */
export type TaskAction = 'transfer' | 'delegate' | 'rollback' | 'resolve' | 'addSign';

/** 办理动作弹窗标题 */
export const ACTION_TITLES: Record<TaskAction, string> = {
  transfer: '转办任务',
  delegate: '委派任务',
  rollback: '回退任务',
  resolve: '处理完成（交回委派人）',
  addSign: '加签',
};

/** 办理动作弹窗表单值 */
export interface ActionFormValues {
  targetUser?: string;
  targetNodeKey?: string;
  comment?: string;
  assignees?: string;
}

/** 加签人文本解析：支持逗号/分号/空白/换行分隔，去空白并去重 */
export const parseAssignees = (raw: string | undefined): string[] =>
  Array.from(
    new Set(
      (raw ?? '')
        .split(/[,，;；\s]+/)
        .map((item) => item.trim())
        .filter(Boolean),
    ),
  );
