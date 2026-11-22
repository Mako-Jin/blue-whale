// 收藏 API

import type { PageResult } from '@bluewhale/http';
import { http } from './client';
import type { DocStarItem } from './types';

/** 收藏接口 */
export const starApi = {
  toggle: (pageId: number) => http.post<{ starred: boolean }>(`/doc/v1/page/${pageId}/star`),
  page: (current = 1, size = 10) =>
    http.get<PageResult<DocStarItem>>('/doc/v1/page/star/page', { current, size }),
};
