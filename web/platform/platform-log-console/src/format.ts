/** 毫秒时间戳 → `HH:mm:ss.SSS` */
export function formatClock(ms: number | undefined | null): string {
  if (ms === undefined || ms === null) return '—';
  const date = new Date(ms);
  const pad = (n: number, width = 2) => String(n).padStart(width, '0');
  return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}.${pad(
    date.getMilliseconds(),
    3,
  )}`;
}

/** 毫秒时间戳 → `YYYY-MM-DD HH:mm:ss.SSS` */
export function formatDateTime(ms: number | undefined | null): string {
  if (ms === undefined || ms === null) return '—';
  const date = new Date(ms);
  const pad = (n: number, width = 2) => String(n).padStart(width, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}:${pad(date.getSeconds())}.${pad(date.getMilliseconds(), 3)}`;
}

/** 相对时间，用于列表里快速判断"多久之前" */
export function formatRelative(ms: number | undefined | null, now = Date.now()): string {
  if (ms === undefined || ms === null) return '—';
  const diff = now - ms;
  if (diff < 0) return '刚刚';
  if (diff < 1000) return `${diff}ms 前`;
  if (diff < 60_000) return `${Math.floor(diff / 1000)}s 前`;
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)}min 前`;
  return `${Math.floor(diff / 3_600_000)}h 前`;
}

/** 对象 → 便于阅读的 JSON 文本 */
export function toPrettyJson(value: unknown): string {
  if (value === undefined || value === null) return '';
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}
