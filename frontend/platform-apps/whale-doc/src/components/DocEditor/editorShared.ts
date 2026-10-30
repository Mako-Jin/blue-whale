// DocEditor 拆分出的共享常量与纯工具函数

import type { JSONContent } from '@tiptap/react';
import type { DocPage, DocPageStatus } from '../../api/types';

/** 自动保存防抖间隔 */
export const AUTOSAVE_MS = 2000;

/** 空文档 */
export const EMPTY_DOC: JSONContent = { type: 'doc', content: [{ type: 'paragraph' }] };

/** 协作快照确认超时 */
export const SNAPSHOT_ACK_TIMEOUT_MS = 8000;

/** 协作光标颜色（与后端 DocPresencePalette 一致） */
const COLLAB_COLORS = ['#2563eb', '#7c3aed', '#db2777', '#ea580c', '#16a34a', '#0891b2', '#ca8a04', '#dc2626'];

/** 按用户ID选择稳定的协作颜色 */
export function collabColor(seed: number | string | undefined): string {
  const value = typeof seed === 'number' ? seed : String(seed ?? '').length;
  return COLLAB_COLORS[Math.abs(value) % COLLAB_COLORS.length];
}

/** 取名字首字作为头像文案 */
export function avatarText(name: string): string {
  return (name || '?').slice(0, 1).toUpperCase();
}

/** 状态文案 */
export const STATUS_LABEL: Record<DocPageStatus, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  ARCHIVED: '已归档',
  PENDING_APPROVAL: '待审批',
};

/** 状态颜色 */
export const STATUS_COLOR: Record<DocPageStatus, string> = {
  DRAFT: 'gold',
  PUBLISHED: 'green',
  ARCHIVED: 'default',
  PENDING_APPROVAL: 'blue',
};

/** 页面是否可编辑（待审批与已归档只读） */
export function isPageEditable(page: DocPage): boolean {
  return page.canEdit && page.status !== 'ARCHIVED' && page.status !== 'PENDING_APPROVAL';
}
