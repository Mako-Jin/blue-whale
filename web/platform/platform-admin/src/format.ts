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
