// PM 子应用通用展示工具：枚举文案、标签颜色、权限判断

import dayjs from 'dayjs';
import type { StatusCategory, Priority, WorkItemType } from './api/pm';
import { getPermissions } from './api/client';

/** 工作项类型选项 */
export const TYPE_OPTIONS: Array<{ value: WorkItemType; label: string }> = [
  { value: 'EPIC', label: '史诗' },
  { value: 'STORY', label: '需求' },
  { value: 'TASK', label: '任务' },
  { value: 'BUG', label: '缺陷' },
  { value: 'SUBTASK', label: '子任务' },
];

/** 优先级选项 */
export const PRIORITY_OPTIONS: Array<{ value: Priority; label: string }> = [
  { value: 'HIGHEST', label: '最高' },
  { value: 'HIGH', label: '高' },
  { value: 'MEDIUM', label: '中' },
  { value: 'LOW', label: '低' },
  { value: 'LOWEST', label: '最低' },
];

/** 项目角色选项 */
export const ROLE_OPTIONS = [
  { value: 'ADMIN', label: '管理员' },
  { value: 'MEMBER', label: '成员' },
  { value: 'VIEWER', label: '只读' },
];

/** 状态分类选项 */
export const CATEGORY_OPTIONS: Array<{ value: StatusCategory; label: string }> = [
  { value: 'TODO', label: '待处理' },
  { value: 'IN_PROGRESS', label: '处理中' },
  { value: 'DONE', label: '已完成' },
];

/** 关联类型选项 */
export const LINK_TYPE_OPTIONS = [
  { value: 'BLOCKS', label: '阻塞' },
  { value: 'BLOCKED_BY', label: '被阻塞' },
  { value: 'RELATES', label: '相关' },
  { value: 'DUPLICATES', label: '重复' },
  { value: 'DUPLICATED_BY', label: '被重复' },
];

/** 工作项类型文案 */
export function typeLabel(type?: string): string {
  return TYPE_OPTIONS.find((option) => option.value === type)?.label ?? type ?? '-';
}

/** 优先级文案 */
export function priorityLabel(priority?: string): string {
  return PRIORITY_OPTIONS.find((option) => option.value === priority)?.label ?? priority ?? '-';
}

/** 优先级标签颜色 */
export function priorityColor(priority?: string): string {
  switch (priority) {
    case 'HIGHEST':
      return 'red';
    case 'HIGH':
      return 'orange';
    case 'MEDIUM':
      return 'blue';
    case 'LOW':
      return 'green';
    default:
      return 'default';
  }
}

/** 状态标签颜色 */
export function statusColor(category?: string): string {
  switch (category) {
    case 'DONE':
      return 'green';
    case 'IN_PROGRESS':
      return 'processing';
    default:
      return 'default';
  }
}

/** 项目状态文案 */
export function projectStatusLabel(status?: string): string {
  return status === 'ARCHIVED' ? '已归档' : '进行中';
}

/** 迭代状态文案 */
export function sprintStatusLabel(status?: string): string {
  switch (status) {
    case 'ACTIVE':
      return '进行中';
    case 'CLOSED':
      return '已关闭';
    default:
      return '未开始';
  }
}

/** 迭代状态标签颜色 */
export function sprintStatusColor(status?: string): string {
  switch (status) {
    case 'ACTIVE':
      return 'processing';
    case 'CLOSED':
      return 'green';
    default:
      return 'default';
  }
}

/** 格式化时间（空值返回 -） */
export function formatTime(value?: string | null): string {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-';
}

/** 格式化日期（空值返回 -） */
export function formatDate(value?: string | null): string {
  return value ? dayjs(value).format('YYYY-MM-DD') : '-';
}

/** 判断是否拥有权限码（无缓存时返回 false，按钮隐藏） */
export function hasPermission(code: string): boolean {
  return getPermissions().includes(code);
}
