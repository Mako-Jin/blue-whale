import dayjs from 'dayjs';

export const date = dayjs;

export function formatDate(date?: dayjs.ConfigType, format = 'YYYY-MM-DD'): string {
  return dayjs(date).format(format);
}

export function formatDateTime(date?: dayjs.ConfigType, format = 'YYYY-MM-DD HH:mm:ss'): string {
  return dayjs(date).format(format);
}

export function isSameDay(a: dayjs.ConfigType, b: dayjs.ConfigType): boolean {
  return dayjs(a).isSame(dayjs(b), 'day');
}

// TODO: 根据项目需要按需引入 dayjs 插件
// import relativeTime from 'dayjs/plugin/relativeTime';
// dayjs.extend(relativeTime);
