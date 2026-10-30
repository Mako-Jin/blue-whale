/**
 * 按应用前缀创建登录态存储（accessToken 内存态 + 用户信息 localStorage）。
 *
 * V-03（中危）：accessToken 此前明文落盘 localStorage，现改为模块级内存变量，
 * 刷新走 HttpOnly Cookie（whale_refresh_token）静默续期（见 refresh.ts）；
 * 用户信息等非敏感数据继续 localStorage，刷新页面后无需重新拉取权限。
 *
 * 各应用保留自己的 `saveLogin`（登录字段合并语义 app 特有），基于本工厂的
 * 原语实现。
 */

import { useEffect, useState } from 'react';
import type { UserInfo } from './types';

/** 工厂允许的最小用户结构（仅要求 username 便于 saveLogin 合并） */
export interface AuthUserLike {
  username?: string;
  nickname?: string;
  roles?: string[];
}

export interface CreateAuthStorageOptions {
  /** 内存 accessToken 的隔离 key（如 whale_admin_access_token；不再落盘 localStorage） */
  accessTokenKey: string;
  /** 用户缓存的 localStorage key（如 whale_admin_user） */
  userKey: string;
}

export interface AuthStorage<U extends AuthUserLike = UserInfo> {
  /** accessToken 的内存隔离 key（调试或跨模块读取用） */
  readonly accessTokenKey: string;
  /** 用户缓存的 key */
  readonly userKey: string;
  /** 读取内存 accessToken（未登录/刷新页面后为 null，直到静默续期成功） */
  getToken(): string | null;
  /** 直接写入内存 accessToken（登录/静默续期用） */
  setToken(token: string): void;
  getCurrentUser(): U | null;
  saveCurrentUser(user: U): void;
  subscribeCurrentUser(listener: () => void): () => void;
  /** React 侧读取当前用户并可响应持久化变更的 hook */
  useCurrentUser(): U | null;
  /** 清除内存令牌与用户缓存（登出 / 401 失效） */
  clearAuth(): void;
}

type Listener = () => void;

/** 内存 accessToken 仓库：按应用 key 隔离，页面刷新即清空（刷新 Cookie 兜底续期） */
const memoryTokens = new Map<string, string>();

/**
 * 创建某应用的登录态存储实例。
 *
 * @typeParam U 应用本地的用户类型（默认共享 {@link UserInfo}）
 */
export function createAuthStorage<U extends AuthUserLike = UserInfo>(
  options: CreateAuthStorageOptions,
): AuthStorage<U> {
  const { accessTokenKey, userKey } = options;
  const listeners = new Set<Listener>();

  function emitUserChange(): void {
    listeners.forEach((listener) => listener());
  }

  function getToken(): string | null {
    return memoryTokens.get(accessTokenKey) ?? null;
  }

  function setToken(token: string): void {
    memoryTokens.set(accessTokenKey, token);
  }

  function getCurrentUser(): U | null {
    const raw = localStorage.getItem(userKey);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as U;
    } catch {
      // 损坏的缓存视为未登录
      return null;
    }
  }

  function saveCurrentUser(user: U): void {
    localStorage.setItem(userKey, JSON.stringify(user));
    emitUserChange();
  }

  function subscribeCurrentUser(listener: Listener): () => void {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  }

  function clearAuth(): void {
    memoryTokens.delete(accessTokenKey);
    localStorage.removeItem(userKey);
    emitUserChange();
  }

  function useCurrentUser(): U | null {
    const [user, setUser] = useState<U | null>(() => getCurrentUser());
    useEffect(() => subscribeCurrentUser(() => setUser(getCurrentUser())), []);
    return user;
  }

  return {
    accessTokenKey,
    userKey,
    getToken,
    setToken,
    getCurrentUser,
    saveCurrentUser,
    subscribeCurrentUser,
    useCurrentUser,
    clearAuth,
  };
}
