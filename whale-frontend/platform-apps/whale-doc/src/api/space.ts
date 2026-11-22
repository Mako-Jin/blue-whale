// 文档空间与成员 API

import type { PageResult } from '@bluewhale/http';
import { http } from './client';
import type { DocMember, DocMemberRole, DocSpace, DocVisibility } from './types';

/** 空间分页查询参数 */
export interface SpaceQuery {
  current?: number;
  size?: number;
  keyword?: string;
  scope?: 'all' | 'mine';
  visibility?: DocVisibility;
}

/** 新建 / 更新空间参数 */
export interface SpaceSavePayload {
  spaceKey?: string;
  name: string;
  description?: string;
  icon?: string;
  visibility?: DocVisibility;
}

/** 空间与成员接口 */
export const spaceApi = {
  page: (query: SpaceQuery) =>
    http.get<PageResult<DocSpace>>('/doc/v1/space/page', query as Record<string, unknown>),
  my: () => http.get<DocSpace[]>('/doc/v1/space/my'),
  detail: (id: number) => http.get<DocSpace>(`/doc/v1/space/${id}`),
  create: (payload: SpaceSavePayload) => http.post<DocSpace>('/doc/v1/space', payload),
  update: (id: number, payload: SpaceSavePayload) =>
    http.put<DocSpace>(`/doc/v1/space/${id}`, payload),
  archive: (id: number) => http.del<void>(`/doc/v1/space/${id}`),
  members: (id: number) => http.get<DocMember[]>(`/doc/v1/space/${id}/member`),
  addMembers: (id: number, userIds: number[], role: DocMemberRole) =>
    http.post<DocMember[]>(`/doc/v1/space/${id}/member`, { userIds, role }),
  updateMember: (spaceId: number, memberId: number, role: DocMemberRole) =>
    http.put<DocMember>(`/doc/v1/space/${spaceId}/member/${memberId}`, { role }),
  removeMember: (spaceId: number, memberId: number) =>
    http.del<void>(`/doc/v1/space/${spaceId}/member/${memberId}`),
};
