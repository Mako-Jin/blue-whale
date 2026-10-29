// 正文与时间的展示工具

import dayjs from 'dayjs';
import type { DocJson } from '../api/types';

/**
 * 解析后端返回的正文 JSON 字符串。
 *
 * <p>后端 content_json 以字符串落库并原样返回，TipTap 需要对象结构，
 * 因此统一在此解析；空值或非法 JSON 返回 null，由编辑器回落空文档。</p>
 */
export function parseDocJson(raw?: string | null): DocJson | null {
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as unknown;
    return parsed !== null && typeof parsed === 'object' ? (parsed as DocJson) : null;
  } catch {
    return null;
  }
}

/** 统一时间展示：空值显示为 - */
export function formatTime(value?: string | null): string {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-';
}
