// 版本历史 API

import type { PageResult } from '@bluewhale/http';
import { http } from './client';
import type { DocPageSaveResult, DocVersion } from './types';

/** 版本历史接口 */
export const versionApi = {
  page: (pageId: number, current = 1, size = 20) =>
    http.get<PageResult<DocVersion>>(`/doc/v1/page/${pageId}/version/page`, { current, size }),
  detail: (pageId: number, revision: number) =>
    http.get<DocVersion>(`/doc/v1/page/${pageId}/version/${revision}`),
  manual: (pageId: number, changeSummary?: string) =>
    http.post<DocVersion>(`/doc/v1/page/${pageId}/version`, { changeSummary }),
  restore: (pageId: number, revision: number) =>
    http.post<DocPageSaveResult>(`/doc/v1/page/${pageId}/version/${revision}/restore`),
};
