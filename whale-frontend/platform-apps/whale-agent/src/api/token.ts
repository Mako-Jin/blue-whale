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

/** 基座注入的登录态读取器（qiankun props；独立运行时为 null） */
let hostTokenGetter: (() => string | null) | null = null;

/** 是否内嵌在基座中运行（mount 时置 true，unmount 复位） */
let embeddedInHost = false;

/**
 * 保存基座注入的宿主属性（qiankun mount 前调用，unmount 后传 null 清空）。
 *
 * <p>基座的 accessToken 是内存态，子应用读不到 localStorage，只能经 props 注入读取器；
 * 独立运行时该读取器为空，回落到本应用自己的存储。</p>
 *
 * @param props 基座注入的 props（含 getToken）；null 表示退出基座
 */
export function setPlatformHostProps(props?: { getToken?: () => string | null } | null): void {
  hostTokenGetter = props?.getToken ?? null;
  embeddedInHost = Boolean(props);
}

/** 是否内嵌在基座中运行（决定是否复用基座的顶栏/登录态） */
export function isEmbeddedInHost(): boolean {
  return embeddedInHost;
}

/** 读取当前登录 token：基座注入优先，其次本应用存储（未登录为 null） */
export function getToken(): string | null {
  return hostTokenGetter?.() ?? storage.getToken();
}

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
