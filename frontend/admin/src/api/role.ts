// 角色管理 API（经网关 → whale-auth /role）

import { del, get, post, put, type PageResult } from './client';

export interface RoleVO {
  id: number;
  roleCode: string;
  roleName: string;
  description?: string;
  sortNo?: number;
  status?: number;
  createTime?: string;
  updateTime?: string;
}

export interface RoleCreatePayload {
  roleCode: string;
  roleName: string;
  description?: string;
  sortNo?: number;
  status?: number;
}

export interface RoleUpdatePayload {
  roleName: string;
  description?: string;
  sortNo?: number;
  status?: number;
}

export interface RolePageParams {
  keyword?: string;
  current: number;
  size: number;
}

export function pageRoles(params: RolePageParams): Promise<PageResult<RoleVO>> {
  return get<PageResult<RoleVO>>('/role/page', { ...params });
}

export function getRole(id: number): Promise<RoleVO> {
  return get<RoleVO>(`/role/${id}`);
}

export function createRole(payload: RoleCreatePayload): Promise<RoleVO> {
  return post<RoleVO>('/role', payload);
}

export function updateRole(id: number, payload: RoleUpdatePayload): Promise<RoleVO> {
  return put<RoleVO>(`/role/${id}`, payload);
}

export function deleteRole(id: number): Promise<void> {
  return del<void>(`/role/${id}`);
}

export function getRolePermissionIds(id: number): Promise<number[]> {
  return get<number[]>(`/role/${id}/permissions`);
}

export function assignRolePermissions(id: number, permissionIds: number[]): Promise<void> {
  return post<void>(`/role/${id}/permissions`, { permissionIds });
}
