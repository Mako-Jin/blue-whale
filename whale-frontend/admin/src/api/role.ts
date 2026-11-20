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
  /** 角色分类：GENERAL 通用 / BUSINESS 业务 / SYSTEM 系统 */
  category?: string;
  /** 内置角色：不可删除、不可改名（三员的权限集亦不可手工调整） */
  builtIn?: boolean;
  /** 三员之一（system_admin / security_admin / audit_admin），互斥置灰依据 */
  threeAdmin?: boolean;
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
  return get<PageResult<RoleVO>>('/role/v1/page', { ...params });
}

export function getRole(id: string): Promise<RoleVO> {
  return get<RoleVO>(`/role/v1/${id}`);
}

export function createRole(payload: RoleCreatePayload): Promise<RoleVO> {
  return post<RoleVO>('/role/v1', payload);
}

export function updateRole(id: string, payload: RoleUpdatePayload): Promise<RoleVO> {
  return put<RoleVO>(`/role/v1/${id}`, payload);
}

export function deleteRole(id: string): Promise<void> {
  return del<void>(`/role/v1/${id}`);
}

export function getRolePermissionIds(id: string): Promise<string[]> {
  return get<string[]>(`/role/v1/${id}/permissions`);
}

export function assignRolePermissions(id: string, permissionIds: string[]): Promise<void> {
  return post<void>(`/role/v1/${id}/permissions`, { permissionIds });
}
