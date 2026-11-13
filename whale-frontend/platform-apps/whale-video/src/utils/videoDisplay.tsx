// 视频展示工具：时长 / 时间 / 状态标签 / 计数格式化

import { Tag, Tooltip } from 'antd';
import type { ContentType, ContentVisibility, CopyrightState, PublishState } from '../api/video';

/** 时长格式化为 mm:ss（超过 1 小时为 hh:mm:ss） */
export function formatDuration(durationMs?: number): string {
  if (!durationMs || durationMs <= 0) {
    return '-';
  }
  const totalSeconds = Math.floor(durationMs / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const pad = (value: number) => String(value).padStart(2, '0');
  return hours > 0 ? `${hours}:${pad(minutes)}:${pad(seconds)}` : `${minutes}:${pad(seconds)}`;
}

/** 时间格式化为 yyyy-MM-dd HH:mm（本地时区）；空值返回 - */
export function formatDateTime(value?: string): string {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  const pad = (item: number) => String(item).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(
    date.getMinutes(),
  )}`;
}

/** 计数格式化：超过 1 万显示为 x.x 万 */
export function formatCount(value?: number): string {
  if (!value || value <= 0) {
    return '0';
  }
  if (value >= 10000) {
    return `${(value / 10000).toFixed(1)}万`;
  }
  return String(value);
}

/** 内容类型文案 */
export function contentTypeLabel(contentType?: ContentType): string {
  if (contentType === 'SHORT_VIDEO') {
    return '短视频';
  }
  if (contentType === 'VIDEO') {
    return '视频';
  }
  if (contentType === 'MOVIE') {
    return '电影';
  }
  return '-';
}

/** 发布状态标签 */
export function renderPublishState(state?: PublishState) {
  if (state === 'PUBLISHED') {
    return <Tag color="green">已发布</Tag>;
  }
  if (state === 'PENDING_REVIEW') {
    return <Tag color="gold">待审核</Tag>;
  }
  if (state === 'OFFLINE') {
    return <Tag>已下架</Tag>;
  }
  return <Tag color="blue">草稿</Tag>;
}

/** 可见性标签 */
export function renderVisibility(visibility?: ContentVisibility) {
  return visibility === 'PRIVATE' ? <Tag>私密</Tag> : <Tag color="cyan">公开</Tag>;
}

/** 版权状态标签（下架原因通过 Tooltip 展示） */
export function renderCopyrightState(state?: CopyrightState, reason?: string) {
  if (state === 'TAKEDOWN') {
    const tag = <Tag color="red">版权下架</Tag>;
    return reason ? <Tooltip title={`下架原因：${reason}`}>{tag}</Tooltip> : tag;
  }
  return <Tag color="green">版权有效</Tag>;
}
