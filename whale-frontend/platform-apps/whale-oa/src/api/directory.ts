// OA 协同 - 通讯录 API 层：按姓名/账号与部门分页查询成员，查询组织架构树
//
// 后端部门树为嵌套结构，children 为子部门列表；用户分页对齐 PageResult。

import { http } from './client';
import type { PageResult } from './client';

export type { PageResult };

/** 通讯录用户 */
export interface OaDirectoryUser {
  id: number;
  username: string;
  nickname?: string;
  deptId?: number;
  deptName?: string;
  phone?: string;
  email?: string;
}

/** 部门树节点 */
export interface OaDeptNode {
  id: number;
  parentId?: number;
  deptName: string;
  children?: OaDeptNode[];
}

/** 通讯录查询 */
export interface OaDirectoryQuery {
  current?: number;
  size?: number;
  keyword?: string;
  deptId?: number;
}

/** 拼装查询串（跳过空值） */
function buildQuery(params: object): string {
  const search = new URLSearchParams();
  Object.entries(params as Record<string, unknown>).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value));
    }
  });
  const query = search.toString();
  return query ? `?${query}` : '';
}

/** 分页查询通讯录用户 */
export function pageDirectoryUsers(
  params: OaDirectoryQuery = {},
): Promise<PageResult<OaDirectoryUser>> {
  return http.get<PageResult<OaDirectoryUser>>(`/oa/v1/directory/users${buildQuery(params)}`);
}

/** 查询组织架构树 */
export function listDepartments(): Promise<OaDeptNode[]> {
  return http.get<OaDeptNode[]>('/oa/v1/directory/depts');
}
