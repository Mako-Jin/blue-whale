/**
 * Cookie 静默续期（V-03）：刷新令牌由服务端下发 HttpOnly Cookie
 * `whale_refresh_token`（Path=/auth，SameSite=Strict），浏览器自动携带；
 * CSRF 令牌经非 HttpOnly Cookie `whale_csrf` 下发，请求 /auth/refresh、
 * /auth/logout 时必须回传 `X-CSRF-Token` 请求头。
 *
 * 本模块是 whale-auth 侧的唯一续期入口：bootstrapAuth（应用启动静默续期）
 * 与各应用显式刷新共用同一次单飞请求，避免并发刷新风暴。
 */

import type { LoginResponse } from './types';

/** 服务端下发的 CSRF Cookie 名（非 HttpOnly，前端可读） */
export const CSRF_COOKIE_NAME = 'whale_csrf';

/** 服务端下发的刷新令牌 Cookie 名（HttpOnly，前端不可读，仅文档用途） */
export const REFRESH_COOKIE_NAME = 'whale_refresh_token';

/** 读取同站 Cookie；不存在返回 null */
export function readCookie(name: string): string | null {
  const cookies = document.cookie.split(';');
  for (const cookie of cookies) {
    const trimmed = cookie.trim();
    const eq = trimmed.indexOf('=');
    if (eq <= 0) {
      continue;
    }
    if (trimmed.slice(0, eq) === name) {
      return decodeURIComponent(trimmed.slice(eq + 1));
    }
  }
  return null;
}

/** 读取当前 CSRF 令牌（whale_csrf Cookie 值；未登录/未下发时为 null） */
export function getCsrfToken(): string | null {
  return readCookie(CSRF_COOKIE_NAME);
}

export interface RefreshAuthOptions {
  /** 后端基地址；生产同源反代、开发 vite proxy，通常传空串 */
  baseUrl?: string;
  /** 刷新成功后的回调（落盘新 access 与用户信息）；失败/未登录不回调 */
  onSuccess?: (data: LoginResponse) => void;
}

/** 单飞刷新中的 Promise：并发调用合并为同一次 /auth/refresh 请求 */
let refreshInFlight: Promise<LoginResponse | null> | null = null;

/**
 * 静默续期：POST /auth/refresh（credentials: 'include' + X-CSRF-Token 头）。
 *
 * 成功返回登录响应（含新 accessToken 与用户信息）并回调 onSuccess；
 * 401/403（Cookie 缺失或已失效，即登录态失效）与其他失败统一返回 null，
 * 由调用方按未登录处理。
 */
export function refreshAccessToken(options?: RefreshAuthOptions): Promise<LoginResponse | null> {
  if (refreshInFlight) {
    return refreshInFlight;
  }
  refreshInFlight = doRefresh(options).finally(() => {
    refreshInFlight = null;
  });
  return refreshInFlight;
}

async function doRefresh(options?: RefreshAuthOptions): Promise<LoginResponse | null> {
  const baseUrl = options?.baseUrl ?? '';
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const csrf = getCsrfToken();
  if (csrf) {
    headers['X-CSRF-Token'] = csrf;
  }
  try {
    const response = await fetch(`${baseUrl}/auth/refresh`, {
      method: 'POST',
      headers,
      credentials: 'include',
    });
    // 401/403：刷新 Cookie 不存在或已失效 → 登录态失效
    if (response.status === 401 || response.status === 403) {
      return null;
    }
    if (!response.ok) {
      return null;
    }
    const payload = (await response.json()) as {
      code?: number;
      message?: string;
      data?: LoginResponse;
    };
    if (!payload || payload.code !== 0 || !payload.data?.accessToken) {
      return null;
    }
    options?.onSuccess?.(payload.data);
    return payload.data;
  } catch {
    // 网络故障等不可续期场景按未登录处理
    return null;
  }
}

export interface BootstrapAuthOptions extends RefreshAuthOptions {
  /** 是否已有内存 accessToken；返回 true 时跳过刷新（默认视为需要刷新） */
  hasToken?: () => boolean;
  /** 刷新成功后写入内存 accessToken */
  setToken?: (token: string) => void;
}

/**
 * 应用启动静默续期：内存无 accessToken 时用刷新 Cookie 换新一次。
 * Cookie 在则恢复登录态（返回 true），不在则按未登录处理（返回 false）。
 */
export async function bootstrapAuth(options?: BootstrapAuthOptions): Promise<boolean> {
  if (options?.hasToken?.()) {
    return true;
  }
  const data = await refreshAccessToken({
    baseUrl: options?.baseUrl,
    onSuccess: (payload) => {
      options?.setToken?.(payload.accessToken);
      options?.onSuccess?.(payload);
    },
  });
  return data !== null;
}
