// 页面与页面树 API

import { http } from './client';
import type {
  DocApprovalInfo,
  DocJson,
  DocPage,
  DocPageSaveResult,
  DocPageTree,
  DocPageType,
} from './types';

/** 新建页面参数 */
export interface PageCreatePayload {
  spaceId: number;
  parentId?: number;
  title: string;
  pageType?: DocPageType;
  slug?: string;
  icon?: string;
  contentJson?: DocJson | null;
  sortNo?: number;
}

/** 保存页面参数 */
export interface PageSavePayload {
  baseRevision: number;
  title?: string;
  contentJson?: DocJson | null;
  changeSummary?: string;
}

/** 页面复制参数（M3） */
export interface PageDuplicatePayload {
  spaceId?: number;
  parentId?: number;
  title?: string;
  includeChildren?: boolean;
}

/** 页面与页面树接口 */
export const pageApi = {
  tree: (spaceId: number) => http.get<DocPageTree[]>(`/doc/space/${spaceId}/tree`),
  detail: (id: number) => http.get<DocPage>(`/doc/page/${id}`),
  bySlug: (spaceId: number, slug: string) =>
    http.get<DocPage>('/doc/page/by-slug', { spaceId, slug }),
  create: (payload: PageCreatePayload) => http.post<DocPage>('/doc/page', payload),
  save: (id: number, payload: PageSavePayload) =>
    http.put<DocPageSaveResult>(`/doc/page/${id}`, payload),
  updateTitle: (id: number, payload: PageSavePayload) =>
    http.put<DocPageSaveResult>(`/doc/page/${id}/title`, payload),
  remove: (id: number) => http.del<void>(`/doc/page/${id}`),
  move: (id: number, parentId: number, sortNo?: number) =>
    http.post<void>(`/doc/page/${id}/move`, { parentId, sortNo }),
  publish: (id: number) => http.post<DocPageSaveResult>(`/doc/page/${id}/publish`),
  archive: (id: number) => http.post<DocPageSaveResult>(`/doc/page/${id}/archive`),
  restore: (id: number) => http.post<DocPageSaveResult>(`/doc/page/${id}/restore`),
  duplicate: (id: number, payload: PageDuplicatePayload) =>
    http.post<DocPage>(`/doc/page/${id}/duplicate`, payload),
  submitApproval: (id: number) => http.post<DocPageSaveResult>(`/doc/page/${id}/submit-approval`),
  approval: (id: number) => http.get<DocApprovalInfo>(`/doc/page/${id}/approval`),
};
