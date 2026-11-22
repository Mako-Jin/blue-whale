// 文档回收站：列表 / 恢复 / 彻底清除 / 清空

import { http, type PageResult } from './client';
import type { DocRecycleItem } from './types';

export const recycleApi = {
  /** 分页查询空间回收站根节点 */
  page: (spaceId: number, current = 1, size = 20) =>
    http.get<PageResult<DocRecycleItem>>(`/doc/v1/space/${spaceId}/recycle/page`, { current, size }),
  /** 恢复一棵回收站子树 */
  restore: (pageId: number) => http.post<void>(`/doc/v1/page/${pageId}/trash/restore`),
  /** 彻底清除一棵回收站子树 */
  purge: (pageId: number) => http.del<void>(`/doc/v1/page/${pageId}/purge`),
  /** 清空空间回收站 */
  clear: (spaceId: number) => http.del<void>(`/doc/v1/space/${spaceId}/recycle`),
};
