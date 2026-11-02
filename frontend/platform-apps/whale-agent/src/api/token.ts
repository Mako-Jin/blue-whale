// 登录态与用户缓存的唯一读写入口（accessToken 内存态 + 用户信息 localStorage）
// 存储与订阅逻辑统一由 @bluewhale/whale-auth 提供，此处仅保留 agent 特有的
// key 前缀（whale_agent_）与登录字段合并语义（审计 P1：消灭多份拷贝）。
// V-03：accessToken 不再落盘 localStorage，静默续期走 HttpOnly 刷新 Cookie。

import { bootstrapAuth as bootstrapAuthShared, createAuthStorage } from '@bluewhale/whale-auth';
import type { AuthMeResponse, LoginResponse, MenuNode, UserInfo } from '@bluewhale/whale-auth';

export type { AuthMeResponse, LoginResponse, MenuNode, UserInfo };

const storage = createAuthStorage<UserInfo>({
  accessTokenKey: 'whale_agent_access_token',
  userKey: 'whale_agent_user',
});

export const getToken = storage.getToken;
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
  storage.setToken(data.accessToken);
  const current = getCurrentUser();
  saveCurrentUser({
    ...(current ?? { username: data.username, nickname: data.nickname, roles: [] }),
    username: data.username || current?.username || '',
    nickname: data.nickname || current?.nickname || '',
    roles: data.roles?.length ? data.roles : (current?.roles ?? []),
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
