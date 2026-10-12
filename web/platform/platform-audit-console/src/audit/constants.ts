import { AuditResults } from 'common-api';
import type { AuditResult } from 'common-api';

/**
 * 审计台的常量。
 *
 * <p>放这里的都是**展示层**的取值（时间窗、每页条数、下拉选项）。
 * 真正的契约标识（权限码、动作标识、结果值）一律来自 `common-api`，
 * 不在本应用里另写一份 —— 那正是"前端不许自造契约标识"这条约束的落点。
 */

/** 时间窗预设 */
export const TIME_WINDOWS = [
  { key: '1h', label: '最近 1 小时', hours: 1 },
  { key: '24h', label: '最近 24 小时', hours: 24 },
  { key: '7d', label: '最近 7 天', hours: 24 * 7 },
] as const;

export type TimeWindowKey = (typeof TIME_WINDOWS)[number]['key'];

export const DEFAULT_WINDOW: TimeWindowKey = '24h';

export function windowMillis(key: TimeWindowKey): number {
  const found = TIME_WINDOWS.find((item) => item.key === key) ?? TIME_WINDOWS[1];
  return found.hours * 60 * 60 * 1000;
}

/** 每页条数。审计是"扫读"型数据，一页给多点比翻页有用 */
export const PAGE_SIZE = 20;

/** 结果下拉的取值（空字符串表示"不限"，而不是 `undefined` —— `<select>` 的 value 不能是 undefined） */
export const RESULT_OPTIONS: Array<{ value: '' | AuditResult; label: string }> = [
  { value: '', label: '全部结果' },
  { value: AuditResults.SUCCESS, label: '成功' },
  { value: AuditResults.FAILURE, label: '失败' },
];
