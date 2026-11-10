// 收藏 API

import type { PageResult } from '@bluewhale/http';
import { http } from './client';
import type { DocStarItem } from './types';

/** 收藏接口 */
export const starApi = {
  toggle: (pageId: number) => http.post<{ starred: boolean }>(`/doc/page/${pageId}/star`),
  page: (current = 1, size = 10) =>
    http.get<PageResult<DocStarItem>>('/doc/page/star/page', { current, size }),
};
