// 运维平台登录态读写入口（localStorage，key 前缀 whale_ops_）
// 存储逻辑统一由 @bluewhale/whale-auth 提供（审计 P1：消灭多份拷贝）；
// ops 无 refresh 流程，仅 access token + 用户缓存。

import { createAuthStorage } from '@bluewhale/whale-auth';

/** GET /auth/me 返回的当前用户信息 */
export interface OpsUserInfo {
  userId?: number | string;
  username: string;
  nickname?: string;
  roles?: string[];
  permissions?: string[];
}

/** POST /auth/login 返回的登录响应 */
export interface LoginResponse {
  accessToken: string;
  tokenType?: string;
  expiresIn?: number;
  username?: string;
  nickname?: string;
  roles?: string[];
}

const storage = createAuthStorage<OpsUserInfo>({
  accessTokenKey: 'whale_ops_token',
  userKey: 'whale_ops_user',
});

export const getToken = storage.getToken;
export const getCurrentUser = storage.getCurrentUser;
export const saveCurrentUser = storage.saveCurrentUser;
export const clearAuth = storage.clearAuth;

/** 登录成功后落盘令牌（独立登录页用，无刷新流程） */
export const saveToken = storage.setToken;

/** 登录成功后落盘令牌与用户基础信息 */
export function saveLogin(data: LoginResponse): void {
  storage.setToken(data.accessToken);
  const current = getCurrentUser();
  storage.saveCurrentUser({
    userId: current?.userId,
    username: data.username ?? current?.username ?? '',
    nickname: data.nickname ?? current?.nickname,
    roles: data.roles ?? current?.roles ?? [],
    permissions: current?.permissions ?? [],
  });
}
