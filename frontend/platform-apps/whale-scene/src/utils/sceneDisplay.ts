// 场景展示辅助：状态 / 类型标签、时间格式化与 2D 坐标兜底
//
// 2D 地图坐标约定：x / y ∈ [0, 1000]，服务端只做范围校验；
// 历史数据缺少坐标时按序号在网格内兜底排布，保证低配/移动端始终可用。

import type {
  EntryType,
  NavigationEntryView,
  NavigationNodeView,
  SceneNodeType,
  SceneStatus,
  VisibilityEffect,
  VisibilitySubjectType,
} from '../api/scene';

interface LabelMeta {
  text: string;
  color: string;
}

/** 场景状态展示 */
export const SCENE_STATUS_META: Record<SceneStatus, LabelMeta> = {
  DRAFT: { text: '草稿', color: 'default' },
  PUBLISHED: { text: '已发布', color: 'green' },
  OFFLINE: { text: '已下线', color: 'orange' },
};

/** 节点类型展示 */
export const NODE_TYPE_META: Record<SceneNodeType, LabelMeta> = {
  REGION: { text: '区域', color: 'blue' },
  BUILDING: { text: '建筑', color: 'geekblue' },
  ENTRANCE: { text: '入口', color: 'green' },
};

/** 入口类型展示 */
export const ENTRY_TYPE_LABEL: Record<EntryType, string> = {
  SYSTEM: '业务系统',
  PAGE: '页面路由',
  KNOWLEDGE: '知识库',
  EXTERNAL: '外部链接',
};

/** 可见性主体类型展示 */
export const SUBJECT_TYPE_LABEL: Record<VisibilitySubjectType, string> = {
  ALL: '全体用户',
  ROLE: '角色',
  DEPT: '部门（首期仅登记）',
  USER: '用户',
};

/** 可见性效果展示 */
export const EFFECT_META: Record<VisibilityEffect, LabelMeta> = {
  ALLOW: { text: '允许', color: 'green' },
  DENY: { text: '拒绝（优先）', color: 'red' },
};

/** 图标标识 → 展示字符（不引入额外图标库，避免 3D/2D 资源耦合） */
const ICON_GLYPHS: Record<string, string> = {
  city: '🏙️',
  oa: '📝',
  erp: '🏭',
  doc: '📚',
  mes: '⚙️',
  pdm: '📦',
  im: '💬',
  pm: '📋',
  contract: '📄',
  music: '🎵',
  video: '🎬',
  agent: '🤖',
  file: '🗂️',
};

/**
 * 将图标标识转换为展示字符；未知图标回落到节点类型默认字符。
 *
 * @param node 导航节点
 * @returns 单字符/emoji 展示值
 */
export function nodeGlyph(node: Pick<NavigationNodeView, 'icon' | 'nodeType'>): string {
  const icon = node.icon?.trim().toLowerCase();
  if (icon && ICON_GLYPHS[icon]) {
    return ICON_GLYPHS[icon];
  }
  if (node.nodeType === 'REGION') {
    return '🏙️';
  }
  if (node.nodeType === 'BUILDING') {
    return '🏛️';
  }
  return '🚪';
}

/**
 * 计算节点在 2D 地图上的百分比坐标；缺省坐标时按序号兜底。
 *
 * @param node 节点
 * @param index 同场景节点序号
 * @param total 节点总数
 * @returns left / top 百分比
 */
export function resolveNodePosition(
  node: Pick<NavigationNodeView, 'x' | 'y'>,
  index: number,
  total: number,
): { left: string; top: string } {
  const x = clampPercent(node.x);
  const y = clampPercent(node.y);
  if (x !== null && y !== null) {
    return { left: `${x}%`, top: `${y}%` };
  }
  const columns = Math.max(2, Math.ceil(Math.sqrt(total)));
  const column = index % columns;
  const row = Math.floor(index / columns);
  const rows = Math.max(1, Math.ceil(total / columns));
  return {
    left: `${((column + 0.5) / columns) * 100}%`,
    top: `${((row + 0.5) / rows) * 100}%`,
  };
}

/** 坐标收敛到 0–100，非法值返回 null（由调用方兜底） */
function clampPercent(value?: number | null): number | null {
  if (typeof value !== 'number' || Number.isNaN(value)) {
    return null;
  }
  return Math.min(100, Math.max(0, value / 10));
}

/**
 * 格式化后端 LocalDateTime 字符串（兼容 ISO 与空格分隔两种形式）。
 *
 * @param value 时间字符串
 * @returns 展示文本；空值返回占位符
 */
export function formatDateTime(value?: string | null): string {
  if (!value) {
    return '—';
  }
  const normalized = value.replace('T', ' ');
  return normalized.length > 19 ? normalized.slice(0, 19) : normalized;
}

/**
 * 打开导航入口：平台内路径同标签跳转，http/https 外链新标签打开。
 *
 * <p>只接受 `/` 开头的平台内路径与 http/https 外链；其余（`javascript:`、`data:`、
 * 协议相对 `//`、相对裸路径）一律忽略，避免前端侧开放重定向。</p>
 *
 * @param entry 导航入口
 * @returns 是否已发起跳转
 */
export function openNavigationEntry(entry: NavigationEntryView): boolean {
  const target = entry.targetPath?.trim();
  if (!target) {
    return false;
  }
  if (target.startsWith('/') && !target.startsWith('//')) {
    window.location.href = target;
    return true;
  }
  if (/^https?:\/\//i.test(target)) {
    window.open(target, '_blank', 'noopener,noreferrer');
    return true;
  }
  return false;
}

/**
 * 按 sortNo、名称排序节点，保证地图与列表顺序稳定。
 *
 * @param nodes 节点集合
 * @returns 新数组
 */
export function sortNodes<T extends { sortNo?: number | null; name: string }>(nodes: T[]): T[] {
  return [...nodes].sort((left, right) => {
    const leftSort = left.sortNo ?? 0;
    const rightSort = right.sortNo ?? 0;
    if (leftSort !== rightSort) {
      return leftSort - rightSort;
    }
    return left.name.localeCompare(right.name, 'zh-Hans-CN');
  });
}
