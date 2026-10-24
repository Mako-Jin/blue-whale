// 展示格式化：时间、日期分隔、文件大小、会话摘要

import type { MessageVO } from '../api/im';

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

function toDate(iso?: string | null): Date | null {
  if (!iso) {
    return null;
  }
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? null : date;
}

/** HH:mm；无效时间返回空字符串 */
export function formatTime(iso?: string | null): string {
  const date = toDate(iso);
  return date ? `${pad(date.getHours())}:${pad(date.getMinutes())}` : '';
}

/** YYYY-MM-DD，用于消息流日期分隔 */
export function formatDay(iso?: string | null): string {
  const date = toDate(iso);
  if (!date) {
    return '';
  }
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** 文件大小（B/KB/MB） */
export function formatSize(bytes?: number | null): string {
  if (!bytes || bytes <= 0) {
    return '0 B';
  }
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

/** 会话列表最后一条消息摘要 */
export function messageSummary(message?: MessageVO | null): string {
  if (!message) {
    return '暂无消息';
  }
  if (message.status === 0) {
    return '[消息已撤回]';
  }
  switch (message.contentType) {
    case 'CARD':
      return '[确认请求]';
    case 'IMAGE':
      return '[图片]';
    case 'FILE':
      return '[文件]';
    case 'MARKDOWN':
    case 'TEXT':
      return message.content.replace(/\s+/g, ' ').slice(0, 60);
    default:
      return '[消息]';
  }
}

/** 消息预览文本（引用条用） */
export function previewText(message?: MessageVO | null): string {
  return messageSummary(message);
}
