// 页面登录内分享 API（M3）

import { http } from './client';
import type { DocPage, DocPageShare } from './types';

/** 创建分享参数 */
export interface ShareCreatePayload {
  /** 有效期天数，0 表示不限期 */
  expireDays?: number;
}

/** 页面分享接口 */
export const shareApi = {
  list: (pageId: number) => http.get<DocPageShare[]>(`/doc/page/${pageId}/share`),
  create: (pageId: number, payload: ShareCreatePayload) =>
    http.post<DocPageShare>(`/doc/page/${pageId}/share`, payload),
  revoke: (pageId: number, shareId: number) =>
    http.del<void>(`/doc/page/${pageId}/share/${shareId}`),
  resolve: (token: string) => http.get<DocPage>(`/doc/share/${encodeURIComponent(token)}`),
};

/** 生成登录内可读的分享完整地址 */
export function shareUrl(sharePath: string): string {
  const origin = window.location.origin;
  return `${origin}/doc${sharePath.replace(/^\/doc/, '')}`;
}
