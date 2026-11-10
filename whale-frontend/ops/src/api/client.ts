// 统一 HTTP 客户端：基于 @bluewhale/http，仅注入 Bearer；HTTP 401 时清态并跳转登录页（无自动续期）

import { createHttpClient } from '@bluewhale/http';
import { clearAuth, getToken } from './token';

const API_BASE: string = import.meta.env.VITE_API_BASE_URL ?? '';

/** 未登录/令牌失效时清态并跳转登录页 */
function redirectToLogin(): void {
  clearAuth();
  const { pathname, search, hash } = window.location;
  if (pathname !== '/login') {
    const from = encodeURIComponent(`${pathname}${search}${hash}`);
    window.location.href = `/login?from=${from}`;
  }
}

const client = createHttpClient({
  baseUrl: API_BASE,
  getToken,
  clearAuth,
  onUnauthorized: redirectToLogin,
});

export type { HttpOptions, PageResult, Result } from '@bluewhale/http';

export const { http, get, post, put, del } = client;
