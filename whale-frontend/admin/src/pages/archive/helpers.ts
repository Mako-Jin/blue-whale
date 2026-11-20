// 档案前端共用常量与格式化工具

import type { ArchiveCategoryVO } from '../../api/archive';
import type { MessageKey } from '../../i18n/messages';
import type { MessageVars } from '../../i18n/translate';

/** 翻译函数类型 */
export type TFunction = (key: MessageKey, vars?: MessageVars) => string;

/** 密级选项 */
export function securityOptions(t: TFunction): { label: string; value: number }[] {
  return [
    { label: t('archive.security.public'), value: 1 },
    { label: t('archive.security.internal'), value: 2 },
    { label: t('archive.security.secret'), value: 3 },
  ];
}

/** 条目状态选项 */
export function itemStatusOptions(t: TFunction): { label: string; value: number }[] {
  return [
    { label: t('archive.itemStatus.pending'), value: 0 },
    { label: t('archive.itemStatus.archived'), value: 1 },
    { label: t('archive.itemStatus.destroyed'), value: 2 },
  ];
}

/** 启用状态下拉选项 */
export function enabledOptions(t: TFunction): { label: string; value: number }[] {
  return [
    { label: t('archive.common.enabled'), value: 1 },
    { label: t('archive.common.disabled'), value: 0 },
  ];
}

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

/** 文件角色文案键 */
const FILE_ROLE_KEYS: Record<string, MessageKey> = {
  ORIGINAL: 'archive.fileRole.original',
  ATTACHMENT: 'archive.fileRole.attachment',
  SNAPSHOT: 'archive.fileRole.snapshot',
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
 * 按密级取值返回展示名。
 *
 * @param value 密级
 * @param t 翻译函数
 * @returns 展示名
 */
export function securityLabel(value: number | undefined, t: TFunction): string {
  return securityOptions(t).find((item) => item.value === value)?.label ?? '-';
}

/**
 * 按条目状态返回展示名。
 *
 * @param value 状态
 * @param t 翻译函数
 * @returns 展示名
 */
export function itemStatusLabel(value: number | undefined, t: TFunction): string {
  return itemStatusOptions(t).find((item) => item.value === value)?.label ?? '-';
}

/**
 * 按文件角色返回展示名，未匹配的原样展示。
 *
 * @param value 文件角色
 * @param t 翻译函数
 * @returns 展示名
 */
export function fileRoleLabel(value: string | undefined, t: TFunction): string {
  const key = value ? FILE_ROLE_KEYS[value] : undefined;
  return key ? t(key) : (value ?? '-');
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
