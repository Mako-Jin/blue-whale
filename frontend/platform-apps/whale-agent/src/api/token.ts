// 登录态与用户缓存的唯一读写入口（localStorage）
// 存储与订阅逻辑统一由 @bluewhale/whale-auth 提供，此处仅保留 agent 特有的
// key 前缀（whale_agent_）与登录字段合并语义（审计 P1：消灭多份拷贝）。

import { createAuthStorage } from '@bluewhale/whale-auth';
import type { AuthMeResponse, LoginResponse, MenuNode, UserInfo } from '@bluewhale/whale-auth';

export type { AuthMeResponse, LoginResponse, MenuNode, UserInfo };

const storage = createAuthStorage<UserInfo>({
  accessTokenKey: 'whale_agent_access_token',
  refreshTokenKey: 'whale_agent_refresh_token',
  userKey: 'whale_agent_user',
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
 * 合并既有用户态（permissions/menus 等派生数据），避免静默续期后动态信息丢失。
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
