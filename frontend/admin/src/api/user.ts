// 用户管理 API（经网关 → whale-service-auth /user）

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

/** 管理员重置口令响应：一次性临时口令仅在本次响应展示（REQ-SEC-023） */
export interface ResetPasswordVO {
  userId: number;
  username: string;
  /** 系统生成的一次性临时口令（明文仅返回这一次，落库仅密文） */
  temporaryPassword: string;
  /** 目标账号下次登录必须改密 */
  mustChangePassword: boolean;
}

export function resetUserPassword(id: number): Promise<ResetPasswordVO> {
  return put<ResetPasswordVO>(`/user/${id}/password`);
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
