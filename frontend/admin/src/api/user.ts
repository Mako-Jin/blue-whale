// 用户管理 API（经网关 → whale-auth /user）

import { del, get, post, put, type PageResult } from './client';

export interface UserVO {
  id: number;
  username: string;
  nickname?: string;
  phone?: string;
  email?: string;
  avatar?: string;
  deptId?: number;
  status?: number;
  lastLoginTime?: string;
  createTime?: string;
  updateTime?: string;
}

export interface UserCreatePayload {
  username: string;
  password: string;
  nickname?: string;
  phone?: string;
  email?: string;
  avatar?: string;
  deptId?: number;
  status?: number;
}

export interface UserUpdatePayload {
  password?: string;
  nickname?: string;
  phone?: string;
  email?: string;
  avatar?: string;
  deptId?: number;
  status?: number;
}

export interface UserPageParams {
  keyword?: string;
  current: number;
  size: number;
}

export function pageUsers(params: UserPageParams): Promise<PageResult<UserVO>> {
  return get<PageResult<UserVO>>('/user/page', { ...params });
}

export function getUser(id: number): Promise<UserVO> {
  return get<UserVO>(`/user/${id}`);
}

export function createUser(payload: UserCreatePayload): Promise<UserVO> {
  return post<UserVO>('/user', payload);
}

export function updateUser(id: number, payload: UserUpdatePayload): Promise<UserVO> {
  return put<UserVO>(`/user/${id}`, payload);
}

export function deleteUser(id: number): Promise<void> {
  return del<void>(`/user/${id}`);
}

export function resetUserPassword(id: number, password: string): Promise<void> {
  return put<void>(`/user/${id}/password`, { password });
}

export function changeUserStatus(id: number, status: number): Promise<void> {
  return put<void>(`/user/${id}/status`, { status });
}

export function getUserRoleIds(id: number): Promise<number[]> {
  return get<number[]>(`/user/${id}/roles`);
}

export function assignUserRoles(id: number, roleIds: number[]): Promise<void> {
  return post<void>(`/user/${id}/roles`, { roleIds });
}
