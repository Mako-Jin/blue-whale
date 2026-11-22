// 搜索 API

import type { PageResult } from '@bluewhale/http';
import { http } from './client';
import type { DocSearchItem } from './types';

/** 文档搜索参数 */
export interface DocSearchQuery {
  keyword: string;
  current?: number;
  size?: number;
  spaceId?: number;
  tagId?: number;
}

/** 文档搜索接口 */
export const searchApi = {
  search: (query: DocSearchQuery) =>
    http.get<PageResult<DocSearchItem>>('/doc/v1/search', { ...query }),
};
