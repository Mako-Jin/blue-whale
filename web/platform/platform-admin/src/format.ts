/** 毫秒时间戳 → 本地时间；空值给占位符，不显示 `Invalid Date` 之类的东西 */
export function formatTime(value?: number | null): string {
  if (typeof value !== 'number' || value <= 0) return '—';
  const date = new Date(value);
  const pad = (n: number): string => String(n).padStart(2, '0');
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}`
  );
}

/** 状态值 → 中文标签 */
export function formatStatus(status?: string | null): string {
  if (status === 'ENABLED') return '启用';
  if (status === 'DISABLED') return '停用';
  return status ?? '—';
}

/**
 * `<input type="datetime-local">` 的值 → 毫秒时间戳。
 *
 * <p>输入框给的是 `YYYY-MM-DDTHH:mm`（本地时间、不带时区），`new Date(...)` 按本地时区
 * 解释它，因此直接取 `getTime()` 即可 —— 后端要的是绝对时刻，时区换算交给 `Date`。
 *
 * <p>空串返回 `undefined` 而不是 `NaN`：`undefined` 在 JSON 里会被整个省略，
 * 语义是"不限制"（立即生效 / 长期有效）；`NaN` 会序列化成 `null`，那是另一回事。
 */
export function parseDateTimeLocal(value: string): number | undefined {
  if (!value) return undefined;
  const time = new Date(value).getTime();
  return Number.isNaN(time) ? undefined : time;
}

/** 毫秒时间戳 → `<input type="datetime-local">` 的值（用于回填编辑态） */
export function toDateTimeLocal(value?: number | null): string {
  if (typeof value !== 'number' || value <= 0) return '';
  const date = new Date(value);
  const pad = (n: number): string => String(n).padStart(2, '0');
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  );
}

/** 生效区间 → 一句话。两个都为空时是"长期有效"，这是最常见的状态 */
export function formatWindow(from?: number | null, to?: number | null): string {
  const start = from ? formatTime(from) : '立即';
  const end = to ? formatTime(to) : '长期';
  if (from == null && to == null) return '长期有效';
  return `${start} → ${end}`;
}
