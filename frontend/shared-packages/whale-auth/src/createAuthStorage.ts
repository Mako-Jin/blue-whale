/**
 * 按应用前缀创建登录态存储（localStorage 唯一读写入口）。
 *
 * admin / platform / agent / form-designer / workflow-designer / ops 此前各自
 * 复制整份 token.ts（约 70-130 行，审计 P1），仅 key 前缀与个别字段不同；
 * 现抽为工厂：类型、订阅、React hook、读写与清理逻辑单份维护。
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
  /** access token 的 localStorage key（如 whale_admin_access_token） */
  accessTokenKey: string;
  /** refresh token 的 localStorage key；不传则 getRefreshToken 恒为 null */
  refreshTokenKey?: string;
  /** 用户缓存的 localStorage key（如 whale_admin_user） */
  userKey: string;
}

export interface AuthStorage<U extends AuthUserLike = UserInfo> {
  /** access token 的 key（调试或跨模块读取用） */
  readonly accessTokenKey: string;
  /** refresh token 的 key（未配置时为 null） */
  readonly refreshTokenKey: string | null;
  /** 用户缓存的 key */
  readonly userKey: string;
  getToken(): string | null;
  getRefreshToken(): string | null;
  /** 直接写入 token（ops 等无刷新流程的登录用） */
  setToken(token: string): void;
  getCurrentUser(): U | null;
  saveCurrentUser(user: U): void;
  subscribeCurrentUser(listener: () => void): () => void;
  /** React 侧读取当前用户并可响应持久化变更的 hook */
  useCurrentUser(): U | null;
  /** 清除令牌与用户缓存（登出 / 401 失效） */
  clearAuth(): void;
}

type Listener = () => void;

/**
 * 创建某应用的登录态存储实例。
 *
 * @typeParam U 应用本地的用户类型（默认共享 {@link UserInfo}）
 */
export function createAuthStorage<U extends AuthUserLike = UserInfo>(
  options: CreateAuthStorageOptions,
): AuthStorage<U> {
  const { accessTokenKey, refreshTokenKey, userKey } = options;
  const listeners = new Set<Listener>();

  function emitUserChange(): void {
    listeners.forEach((listener) => listener());
  }

  function getToken(): string | null {
    return localStorage.getItem(accessTokenKey);
  }

  function getRefreshToken(): string | null {
    return refreshTokenKey ? localStorage.getItem(refreshTokenKey) : null;
  }

  function setToken(token: string): void {
    localStorage.setItem(accessTokenKey, token);
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
    localStorage.removeItem(accessTokenKey);
    if (refreshTokenKey) {
      localStorage.removeItem(refreshTokenKey);
    }
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
    refreshTokenKey: refreshTokenKey ?? null,
    userKey,
    getToken,
    getRefreshToken,
    setToken,
    getCurrentUser,
    saveCurrentUser,
    subscribeCurrentUser,
    useCurrentUser,
    clearAuth,
  };
}
