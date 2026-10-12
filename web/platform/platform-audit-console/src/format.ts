import { AuditResults } from 'common-api';
import type { AuditResult } from 'common-api';

/**
 * 审计台的展示格式化。
 *
 * <p>与权限管理台的 `format.ts` 相比，这里多两件事，都是审计数据特有的：
 * **秒级时间**（审计靠时间相邻来判断"刚才那一下被记下了"，
 * 只到分钟的话同一次演练里的几条记录看起来一模一样）与**耗时**。
 */

/** 毫秒时间戳 → 本地时间（到秒）。空值给占位符，不显示 `Invalid Date` */
export function formatTime(value?: number | null): string {
  if (typeof value !== 'number' || value <= 0) return '—';
  const date = new Date(value);
  const pad = (n: number): string => String(n).padStart(2, '0');
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  );
}

/** 只到秒的时间（当天记录用，省掉日期那一截） */
export function formatClock(value?: number | null): string {
  if (typeof value !== 'number' || value <= 0) return '—';
  const date = new Date(value);
  const pad = (n: number): string => String(n).padStart(2, '0');
  return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

/** 耗时：小于 1 秒给毫秒，否则给秒（保留一位小数） */
export function formatDuration(value?: number | null): string {
  if (typeof value !== 'number' || value < 0) return '—';
  if (value < 1000) return `${value} ms`;
  return `${(value / 1000).toFixed(1)} s`;
}

/** 结果 → 中文标签 */
export function formatResult(result?: AuditResult | null): string {
  if (result === AuditResults.SUCCESS) return '成功';
  if (result === AuditResults.FAILURE) return '失败';
  return result ?? '—';
}

/**
 * 结果 → 样式修饰符。
 *
 * <p>与日志台的级别标签同一个思路：**失败必须是红色的**。
 * 审计表里失败与成功混在一起，靠文字区分在快速扫读时几乎看不见，
 * 而"哪些操作被拒绝了"恰恰是审计最常被翻的一条线。
 */
export function resultModifier(result?: AuditResult | null): 'ok' | 'bad' | 'plain' {
  if (result === AuditResults.SUCCESS) return 'ok';
  if (result === AuditResults.FAILURE) return 'bad';
  return 'plain';
}

/** 空值统一给占位符，避免表格里出现 `undefined` / `null` 字样 */
export function orDash(value?: string | number | null): string {
  if (value === null || value === undefined || value === '') return '—';
  return String(value);
}
