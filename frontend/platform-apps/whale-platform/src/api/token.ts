// 基座登录态与用户缓存的唯一读写入口（accessToken 内存态 + 用户信息 localStorage）
// 键名统一使用 whale_platform_ 前缀，与 admin / 子应用登录态隔离，避免串号。
// 存储与订阅逻辑统一由 @bluewhale/whale-auth 提供（审计 P1：消灭多份拷贝）。
// V-03：accessToken 不再落盘 localStorage，静默续期走 HttpOnly 刷新 Cookie；
// 子应用经 qiankun props 的 getToken 读取基座内存令牌。

import {
  bootstrapAuth as bootstrapAuthShared,
  createAuthStorage,
} from '@bluewhale/whale-auth';
import type { AuthMeResponse, LoginResponse, MenuNode, UserInfo } from '@bluewhale/whale-auth';

export type { AuthMeResponse, LoginResponse, MenuNode, UserInfo };

const storage = createAuthStorage<UserInfo>({
  accessTokenKey: 'whale_platform_access_token',
  userKey: 'whale_platform_user',
});

export const getToken = storage.getToken;
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
