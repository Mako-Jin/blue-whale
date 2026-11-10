// 运维平台登录态读写入口（accessToken 内存态 + 用户信息 localStorage，key 前缀 whale_ops_）
// 存储逻辑统一由 @bluewhale/whale-auth 提供（审计 P1：消灭多份拷贝）。
// V-03：accessToken 不再落盘 localStorage，应用启动经刷新 Cookie 静默续期。

import { bootstrapAuth as bootstrapAuthShared, createAuthStorage } from '@bluewhale/whale-auth';

/** GET /auth/me 返回的当前用户信息 */
export interface OpsUserInfo {
  userId?: number | string;
  username: string;
  nickname?: string;
  roles?: string[];
  permissions?: string[];
}

/** POST /auth/login 返回的登录响应（刷新令牌在 HttpOnly Cookie，不在响应体） */
export interface LoginResponse {
  accessToken: string;
  tokenType?: string;
  expiresIn?: number;
  userId?: number | string;
  username?: string;
  nickname?: string;
  roles?: string[];
  /** 首次登录/管理员重置密码后强制改密标记（T-03） */
  mustChangePassword?: boolean;
}

const storage = createAuthStorage<OpsUserInfo>({
  accessTokenKey: 'whale_ops_token',
  userKey: 'whale_ops_user',
});

export const getToken = storage.getToken;
export const getCurrentUser = storage.getCurrentUser;
export const saveCurrentUser = storage.saveCurrentUser;
export const clearAuth = storage.clearAuth;

/** 登录成功后落盘令牌（内存；独立登录页用） */
export const saveToken = storage.setToken;

/** 登录成功后落盘令牌与用户基础信息 */
export function saveLogin(data: LoginResponse): void {
  storage.setToken(data.accessToken);
  const current = getCurrentUser();
  storage.saveCurrentUser({
    userId: data.userId ?? current?.userId,
    username: data.username ?? current?.username ?? '',
    nickname: data.nickname ?? current?.nickname,
    roles: data.roles ?? current?.roles ?? [],
    permissions: current?.permissions ?? [],
  });
}

/** 应用启动静默续期：内存无令牌时用刷新 Cookie 换新（Cookie 不在则按未登录处理） */
export function bootstrapAuth(baseUrl = ''): Promise<boolean> {
  return bootstrapAuthShared({
    hasToken: () => storage.getToken() !== null,
    setToken: storage.setToken,
    baseUrl,
    onSuccess: (data) => saveLogin(data),
  });
}
