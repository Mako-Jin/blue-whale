// 文档模板 API（M3）

import { http } from './client';
import type { DocJson, DocPage, DocPageType, DocTemplate } from './types';

/** 模板保存参数 */
export interface TemplateSavePayload {
  spaceId?: number;
  name: string;
  description?: string;
  pageType?: DocPageType;
  icon?: string;
  contentJson?: DocJson | null;
  status?: number;
  sortNo?: number;
}

/** 从模板创建页面参数 */
export interface TemplateApplyPayload {
  spaceId: number;
  parentId?: number;
  title?: string;
  slug?: string;
}

/** 文档模板接口 */
export const templateApi = {
  list: (spaceId?: number) =>
    http.get<DocTemplate[]>('/doc/v1/template', spaceId ? { spaceId } : undefined),
  detail: (id: number) => http.get<DocTemplate>(`/doc/v1/template/${id}`),
  create: (payload: TemplateSavePayload) => http.post<DocTemplate>('/doc/v1/template', payload),
  update: (id: number, payload: TemplateSavePayload) =>
    http.put<DocTemplate>(`/doc/v1/template/${id}`, payload),
  remove: (id: number) => http.del<void>(`/doc/v1/template/${id}`),
  apply: (id: number, payload: TemplateApplyPayload) =>
    http.post<DocPage>(`/doc/v1/template/${id}/apply`, payload),
};
