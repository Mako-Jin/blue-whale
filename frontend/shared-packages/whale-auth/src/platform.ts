/**
 * 基座（whale-platform）登录态 storage key 常量与只读器。
 *
 * 子应用（contract/doc/erp/mes/oa/pdm/pm/portal 等）此前各自硬编码这些字符串
 * （审计 P1），key 改名时需逐文件排查；现统一从这里导入。
 */

/** 基座 access token 的 localStorage key */
export const PLATFORM_ACCESS_TOKEN_KEY = 'whale_platform_access_token';

/** 基座 refresh token 的 localStorage key */
export const PLATFORM_REFRESH_TOKEN_KEY = 'whale_platform_refresh_token';

/** 基座用户缓存的 localStorage key */
export const PLATFORM_USER_KEY = 'whale_platform_user';

/** 基座键集合（便于一次解构） */
export const PLATFORM_AUTH_KEYS = {
  accessToken: PLATFORM_ACCESS_TOKEN_KEY,
  refreshToken: PLATFORM_REFRESH_TOKEN_KEY,
  user: PLATFORM_USER_KEY,
} as const;

/** 读取基座缓存 token（子应用请求头注入用；未登录返回 null） */
export function getPlatformToken(): string | null {
  return localStorage.getItem(PLATFORM_ACCESS_TOKEN_KEY);
}

/**
 * 读取基座缓存的当前用户（只读；无缓存或解析失败返回 null）。
 *
 * @typeParam U 期望的用户结构，各子应用可用本地窄化类型（如 ContractUser）
 */
export function getPlatformUser<U = unknown>(): U | null {
  const raw = localStorage.getItem(PLATFORM_USER_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as U;
  } catch {
    // 损坏的缓存直接忽略
    return null;
  }
}
