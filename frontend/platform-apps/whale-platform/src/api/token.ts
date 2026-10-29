// 基座登录态与用户缓存的唯一读写入口（localStorage）
// 键名统一使用 whale_platform_ 前缀，与 admin / 子应用登录态隔离，避免串号。
// 存储与订阅逻辑统一由 @bluewhale/whale-auth 提供（审计 P1：消灭多份拷贝）。

import { createAuthStorage } from '@bluewhale/whale-auth';
import type { AuthMeResponse, LoginResponse, MenuNode, UserInfo } from '@bluewhale/whale-auth';

export type { AuthMeResponse, LoginResponse, MenuNode, UserInfo };

const storage = createAuthStorage<UserInfo>({
  accessTokenKey: 'whale_platform_access_token',
  refreshTokenKey: 'whale_platform_refresh_token',
  userKey: 'whale_platform_user',
});

export const getToken = storage.getToken;
export const getRefreshToken = storage.getRefreshToken;
export const getCurrentUser = storage.getCurrentUser;
export const subscribeCurrentUser = storage.subscribeCurrentUser;
export const useCurrentUser = storage.useCurrentUser;
export const saveCurrentUser = storage.saveCurrentUser;
export const clearAuth = storage.clearAuth;

/**
 * 登录/刷新成功后落盘令牌与用户基础信息。
 * 合并既有用户态（permissions/menus 等派生数据），避免静默续期后权限码丢失。
 */
export function saveLogin(data: LoginResponse): void {
  localStorage.setItem(storage.accessTokenKey, data.accessToken);
  if (storage.refreshTokenKey) {
    localStorage.setItem(storage.refreshTokenKey, data.refreshToken);
  }
  const current = getCurrentUser();
  saveCurrentUser({
    ...(current ?? { username: data.username, nickname: data.nickname, roles: [] }),
    username: data.username || current?.username || '',
    nickname: data.nickname || current?.nickname || '',
    roles: data.roles?.length ? data.roles : (current?.roles ?? []),
  });
}
