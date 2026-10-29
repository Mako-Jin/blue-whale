// 页面洞察 API：动态 / 反向链接 / 最近访问（M2-5）

import type { PageResult } from '@bluewhale/http';
import { http } from './client';
import type { DocActivity, DocBacklink, DocRecent } from './types';

/** 页面洞察接口 */
export const insightApi = {
  activity: (pageId: number, current = 1, size = 20) =>
    http.get<PageResult<DocActivity>>(`/doc/page/${pageId}/activity`, { current, size }),
  backlinks: (pageId: number) => http.get<DocBacklink[]>(`/doc/page/${pageId}/backlink`),
  recent: (limit = 10) => http.get<DocRecent[]>('/doc/recent', { limit }),
};
