// 档案前端共用常量与格式化工具

import type { ArchiveCategoryVO } from '../../api/archive';

/** 密级选项 */
export const SECURITY_OPTIONS = [
  { label: '公开', value: 1 },
  { label: '内部', value: 2 },
  { label: '秘密', value: 3 },
];

/** 条目状态选项 */
export const ITEM_STATUS_OPTIONS = [
  { label: '待整理', value: 0 },
  { label: '已归档', value: 1 },
  { label: '已销毁', value: 2 },
];

/** 密级标签颜色 */
export const SECURITY_COLORS: Record<number, string> = {
  1: 'green',
  2: 'blue',
  3: 'red',
};

/** 条目状态标签颜色 */
export const ITEM_STATUS_COLORS: Record<number, string> = {
  0: 'orange',
  1: 'green',
  2: 'default',
};

/** 文件角色中文名 */
export const FILE_ROLE_LABELS: Record<string, string> = {
  ORIGINAL: '原件',
  ATTACHMENT: '附件',
  SNAPSHOT: '快照',
};

/** 门类下拉选项 */
export interface CategoryOption {
  label: string;
  value: string;
}

/**
 * 将门类树拍平为缩进式下拉选项。
 *
 * @param nodes 门类树
 * @param depth 当前层级（内部递归用）
 * @returns 下拉选项数组
 */
export function flattenCategories(nodes: ArchiveCategoryVO[], depth = 0): CategoryOption[] {
  return nodes.flatMap((node) => {
    const prefix = depth > 0 ? `${'　'.repeat(depth)}` : '';
    const current: CategoryOption = {
      label: `${prefix}${node.name}（${node.code}）`,
      value: node.id,
    };
    const children = node.children?.length ? flattenCategories(node.children, depth + 1) : [];
    return [current, ...children];
  });
}

/**
 * 按密级取值返回中文名。
 *
 * @param value 密级
 * @returns 中文名
 */
export function securityLabel(value?: number): string {
  return SECURITY_OPTIONS.find((item) => item.value === value)?.label ?? '-';
}

/**
 * 按条目状态返回中文名。
 *
 * @param value 状态
 * @returns 中文名
 */
export function itemStatusLabel(value?: number): string {
  return ITEM_STATUS_OPTIONS.find((item) => item.value === value)?.label ?? '-';
}

/**
 * 格式化时间：去掉毫秒与 T 分隔符。
 *
 * @param value ISO 时间
 * @returns 展示文本
 */
export function formatDateTime(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '-';
}
