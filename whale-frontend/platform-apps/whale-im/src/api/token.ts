// 登录态解析：使用基座注入的读取器（V-03：基座 accessToken 为内存态，
// 经 qiankun props 共享，不再有 token 的 localStorage 回退）

import { PLATFORM_USER_KEY } from '@bluewhale/whale-auth';
import { getHostProps } from '../platform/host';

const IM_USER_KEY = 'whale_im_user';

interface CachedUser {
  userId?: number | string;
  username?: string;
  nickname?: string;
  permissions?: string[];
}

/** 读取当前登录令牌；无登录态返回 null */
export function getToken(): string | null {
  return getHostProps()?.getToken?.() ?? null;
}

/** 解析缓存的用户信息（先基座键，再子应用自有键） */
function readCachedUser(): CachedUser | null {
  for (const key of [PLATFORM_USER_KEY, IM_USER_KEY]) {
    const raw = localStorage.getItem(key);
    if (!raw) {
      continue;
    }
    try {
      return JSON.parse(raw) as CachedUser;
    } catch {
      // 损坏的缓存直接忽略，继续尝试下一个键
    }
  }
  return null;
}

/** 读取当前用户ID（字符串）；未知返回 null */
export function getCurrentUserId(): string | null {
  const fromHost = getHostProps()?.getUserId?.() ?? null;
  if (fromHost) {
    return fromHost;
  }
  const userId = readCachedUser()?.userId;
  return userId === undefined || userId === null ? null : String(userId);
}

/** 读取权限码集合：基座注入优先，其次缓存用户信息 */
export function getPermissions(): string[] {
  const fromHost = getHostProps()?.getPermissions?.() ?? [];
  if (fromHost.length > 0) {
    return fromHost;
  }
  return readCachedUser()?.permissions ?? [];
}
