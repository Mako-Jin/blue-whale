// 文档标签 API（M2-5）

import { http } from './client';
import type { DocTag } from './types';

/** 创建 / 更新标签参数 */
export interface DocTagPayload {
  name: string;
  color?: string;
}

/** 标签接口 */
export const tagApi = {
  list: (spaceId: number) => http.get<DocTag[]>(`/doc/space/${spaceId}/tag`),
  create: (spaceId: number, payload: DocTagPayload) =>
    http.post<DocTag>(`/doc/space/${spaceId}/tag`, payload),
  update: (tagId: number, payload: DocTagPayload) =>
    http.put<DocTag>(`/doc/tag/${tagId}`, payload),
  remove: (tagId: number) => http.del<void>(`/doc/tag/${tagId}`),
  pageTags: (pageId: number) => http.get<DocTag[]>(`/doc/page/${pageId}/tag`),
  updatePageTags: (pageId: number, tagIds: number[]) =>
    http.put<DocTag[]>(`/doc/page/${pageId}/tag`, { tagIds }),
};
