// 合同子应用通用展示工具：枚举文案、标签颜色、权限判断、日期格式化

import dayjs from 'dayjs';
import type { ContractStatus } from './api/contract';
import { getPermissions } from './api/client';

/** 合同类别选项 */
export const CATEGORY_OPTIONS = [
  { value: 'PURCHASE', label: '采购合同' },
  { value: 'SALES', label: '销售合同' },
  { value: 'SERVICE', label: '服务合同' },
  { value: 'LEASE', label: '租赁合同' },
  { value: 'OTHER', label: '其他合同' },
];

/** 合同状态选项（筛选用） */
export const STATUS_OPTIONS: Array<{ value: ContractStatus; label: string }> = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'APPROVING', label: '审批中' },
  { value: 'APPROVED', label: '审批通过' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'WITHDRAWN', label: '已撤回' },
  { value: 'SIGNING', label: '签署中' },
  { value: 'SIGNED', label: '已签署' },
  { value: 'EFFECTIVE', label: '已生效' },
  { value: 'ARCHIVED', label: '已归档' },
  { value: 'TERMINATED', label: '已终止' },
  { value: 'VOID', label: '已作废' },
];

/** 相对方类型选项 */
export const PARTY_TYPE_OPTIONS = [
  { value: 'A', label: '甲方' },
  { value: 'B', label: '乙方' },
  { value: 'OTHER', label: '其他' },
];

/** 合同类别文案 */
export function categoryLabel(category?: string): string {
  return CATEGORY_OPTIONS.find((option) => option.value === category)?.label ?? category ?? '-';
}

/** 合同状态文案 */
export function statusLabel(status?: string): string {
  return STATUS_OPTIONS.find((option) => option.value === status)?.label ?? status ?? '-';
}

/** 合同状态标签颜色 */
export function statusColor(status?: string): string {
  switch (status) {
    case 'APPROVING':
    case 'SIGNING':
      return 'processing';
    case 'APPROVED':
    case 'SIGNED':
      return 'blue';
    case 'EFFECTIVE':
      return 'green';
    case 'REJECTED':
    case 'TERMINATED':
    case 'VOID':
      return 'red';
    case 'ARCHIVED':
      return 'default';
    case 'WITHDRAWN':
      return 'orange';
    default:
      return 'default';
  }
}

/** 相对方类型文案 */
export function partyTypeLabel(type?: string): string {
  return PARTY_TYPE_OPTIONS.find((option) => option.value === type)?.label ?? type ?? '-';
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
