// 登录态与用户缓存的唯一读写入口（localStorage）——复用 admin/form-designer 版，仅更换 storage key 前缀

import { useEffect, useState } from 'react';

const ACCESS_TOKEN_KEY = 'whale_wf_access_token';
const REFRESH_TOKEN_KEY = 'whale_wf_refresh_token';
const USER_KEY = 'whale_wf_user';

/** 后端 MenuVO：GET /auth/me 返回的可见菜单树节点 */
export interface MenuNode {
  id: number;
  parentId?: number;
  name: string;
  path?: string;
  component?: string;
  icon?: string;
  sort?: number;
  menuType?: string;
  permissionCode?: string;
  visible?: number;
  status?: number;
  children?: MenuNode[];
}

export interface UserInfo {
  userId?: number | string;
  username: string;
  nickname: string;
  roles: string[];
  permissions?: string[];
  menus?: MenuNode[];
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  username: string;
  nickname: string;
  roles: string[];
}

export interface AuthMeResponse {
  userId: number | string;
  username: string;
  nickname: string;
  roles: string[];
  permissions: string[];
  menus: MenuNode[];
}

export function getToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function getCurrentUser(): UserInfo | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as UserInfo;
  } catch {
    return null;
  }
}

/** 用户态变更订阅（供 React 侧响应登录/登出等持久化变更） */
type UserListener = () => void;
const userListeners = new Set<UserListener>();

export function subscribeCurrentUser(listener: UserListener): () => void {
  userListeners.add(listener);
  return () => {
    userListeners.delete(listener);
  };
}

function emitUserChange(): void {
  userListeners.forEach((listener) => listener());
}

/** React 侧读取当前用户并可响应持久化变更的 hook */
export function useCurrentUser(): UserInfo | null {
  const [user, setUser] = useState<UserInfo | null>(() => getCurrentUser());
  useEffect(() => subscribeCurrentUser(() => setUser(getCurrentUser())), []);
  return user;
}

export function saveCurrentUser(user: UserInfo): void {
  localStorage.setItem(USER_KEY, JSON.stringify(user));
  emitUserChange();
}

/**
 * 登录/刷新成功后落盘令牌与用户基础信息。
 * 合并既有用户态（permissions/menus 等派生数据），避免静默续期后动态信息丢失。
 */
export function saveLogin(data: LoginResponse): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, data.accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, data.refreshToken);
  const current = getCurrentUser();
  saveCurrentUser({
    ...(current ?? { username: data.username, nickname: data.nickname, roles: [] }),
    username: data.username || current?.username || '',
    nickname: data.nickname || current?.nickname || '',
    roles: data.roles?.length ? data.roles : (current?.roles ?? []),
  });
}

export function clearAuth(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
  emitUserChange();
}
