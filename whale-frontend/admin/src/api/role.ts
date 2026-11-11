// 角色管理 API（经网关 → whale-service-auth /role）

import { del, get, post, put, type PageResult } from './client';

export interface RoleVO {
  /** 后端 Long 序列化为字符串（V10 角色基线如 "26001"） */
  id: string;
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

export function getRole(id: string): Promise<RoleVO> {
  return get<RoleVO>(`/role/${id}`);
}

export function createRole(payload: RoleCreatePayload): Promise<RoleVO> {
  return post<RoleVO>('/role', payload);
}

export function updateRole(id: string, payload: RoleUpdatePayload): Promise<RoleVO> {
  return put<RoleVO>(`/role/${id}`, payload);
}

export function deleteRole(id: string): Promise<void> {
  return del<void>(`/role/${id}`);
}

export function getRolePermissionIds(id: string): Promise<string[]> {
  return get<string[]>(`/role/${id}/permissions`);
}

export function assignRolePermissions(id: string, permissionIds: string[]): Promise<void> {
  return post<void>(`/role/${id}/permissions`, { permissionIds });
}
