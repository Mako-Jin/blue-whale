// 登录态解析：优先使用基座注入的读取器，其次同源 localStorage（生产同源部署时可用）
//
// 基座键：whale_platform_access_token / whale_platform_user
// 子应用自有键：whale_im_access_token / whale_im_user（独立调试时可选落盘，不作为主路径）

import { PLATFORM_ACCESS_TOKEN_KEY, PLATFORM_USER_KEY } from '@bluewhale/whale-auth';
import { getHostProps } from '../platform/host';

const IM_TOKEN_KEY = 'whale_im_access_token';
const IM_USER_KEY = 'whale_im_user';

interface CachedUser {
  userId?: number | string;
  username?: string;
  nickname?: string;
  permissions?: string[];
}

/** 读取当前登录令牌；无登录态返回 null */
export function getToken(): string | null {
  const fromHost = getHostProps()?.getToken?.() ?? null;
  if (fromHost) {
    return fromHost;
  }
  return localStorage.getItem(PLATFORM_ACCESS_TOKEN_KEY) ?? localStorage.getItem(IM_TOKEN_KEY);
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
