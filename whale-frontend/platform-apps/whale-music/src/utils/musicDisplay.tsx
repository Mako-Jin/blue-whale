// 音乐展示辅助：时长格式化与状态 / 可见性 / 标签渲染（曲库、我的作品、详情共用）

import { Space, Tag } from 'antd';
import type { CopyrightState, PublishState, SongVisibility, TagView } from '../api/music';

/** 毫秒时长 → mm:ss */
export function formatDuration(durationMs?: number): string {
  if (!durationMs || durationMs <= 0) {
    return '-';
  }
  const totalSeconds = Math.round(durationMs / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

/** 可见性标签：公开 / 自听 */
export function renderVisibility(visibility?: SongVisibility) {
  if (!visibility) {
    return '-';
  }
  return visibility === 'PUBLIC' ? <Tag color="blue">公开</Tag> : <Tag color="orange">自听</Tag>;
}

/** 发布状态标签 */
export function renderPublishState(publishState?: PublishState) {
  const map: Record<PublishState, { color: string; label: string }> = {
    DRAFT: { color: 'default', label: '草稿' },
    PUBLISHED: { color: 'success', label: '已上架' },
    OFFLINE: { color: 'error', label: '已下架' },
  };
  if (!publishState) {
    return '-';
  }
  const item = map[publishState];
  return <Tag color={item.color}>{item.label}</Tag>;
}

/** 版权状态标签：已声明 / 有效 / 版权下架 */
export function renderCopyrightState(copyrightState?: CopyrightState) {
  const map: Record<CopyrightState, { color: string; label: string }> = {
    DECLARED: { color: 'gold', label: '已声明' },
    VALID: { color: 'success', label: '有效' },
    TAKEDOWN: { color: 'error', label: '版权下架' },
  };
  if (!copyrightState) {
    return '-';
  }
  const item = map[copyrightState];
  return <Tag color={item.color}>{item.label}</Tag>;
}

/** 标签集合渲染 */
export function renderTags(tags?: TagView[]) {
  if (!tags?.length) {
    return '-';
  }
  return (
    <Space size={4} wrap className="music-tags">
      {tags.map((tag) => (
        <Tag key={tag.id}>{tag.name}</Tag>
      ))}
    </Space>
  );
}
