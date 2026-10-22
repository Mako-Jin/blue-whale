// 权限点 API（经网关 → whale-auth /permission）

import { del, get, post, put, type PageResult } from './client';

export interface PermissionVO {
  id: number;
  parentId: number;
  permCode: string;
  permName: string;
  permType?: string;
  path?: string;
  method?: string;
  sortNo?: number;
  status?: number;
  createTime?: string;
  updateTime?: string;
}

export interface PermissionPageParams {
  keyword?: string;
  permType?: string;
  current: number;
  size: number;
}

/** 权限类型：SYSTEM / MENU / API / BUTTON / DATA / ATTR */
export type PermissionType = 'SYSTEM' | 'MENU' | 'API' | 'BUTTON' | 'DATA' | 'ATTR';

/** 新增/更新权限点请求体 */
export interface PermissionPayload {
  parentId?: number;
  permCode: string;
  permName: string;
  permType: PermissionType | string;
  path?: string;
  method?: string;
  sortNo?: number;
  status?: number;
}

export function pagePermissions(params: PermissionPageParams): Promise<PageResult<PermissionVO>> {
  return get<PageResult<PermissionVO>>('/permission/page', { ...params });
}

export function createPermission(body: PermissionPayload): Promise<void> {
  return post<void>('/permission', body);
}

export function updatePermission(id: number, body: PermissionPayload): Promise<void> {
  return put<void>(`/permission/${id}`, body);
}

export function deletePermission(id: number): Promise<void> {
  return del<void>(`/permission/${id}`);
}
