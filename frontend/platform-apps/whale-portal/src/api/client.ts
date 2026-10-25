// 门户子应用请求层：基于 @bluewhale/http，保留原 request(url, init) 语义
//
// 后端统一返回 `Result` 信封：{ code: number; message: string; data: T }
//   - HTTP 状态恒 200，业务码在 body.code；
//   - code === 0 表示成功（取 body.data），否则 throw ApiError。
// token 从 localStorage 读取（key 与基座 whale-platform 保持一致），
// 拼到 `Authorization: Bearer <token>` 头。
// 前端统一使用相对路径请求（如 `/workflow/task/page`），
// 由 dev server / 生产 Nginx 经网关 8080 代理到对应后端服务。

import { createHttpClient, type HttpOptions } from '@bluewhale/http';

/** 基座 token 存储 key（对齐 frontend/platform-apps/whale-platform/src/api/token.ts） */
const TOKEN_STORAGE_KEY = 'whale_platform_access_token';

/** 基座用户缓存 key（对齐 whale-platform/src/api/token.ts，只读） */
const USER_STORAGE_KEY = 'whale_platform_user';

/** 基座缓存的当前用户（仅取权限判定所需字段） */
export interface PortalUser {
  userId?: number | string;
  username?: string;
  nickname?: string;
  roles?: string[];
  permissions?: string[];
}

/** 读取当前登录 token（未登录时为 null） */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_STORAGE_KEY);
}

/** 读取基座缓存的当前用户（无缓存或解析失败时为 null） */
export function getCurrentUser(): PortalUser | null {
  const raw = localStorage.getItem(USER_STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as PortalUser;
  } catch {
    return null;
  }
}

/** 判断当前用户是否具备指定权限码 */
export function hasPermission(code: string): boolean {
  return getCurrentUser()?.permissions?.includes(code) ?? false;
}

const client = createHttpClient({ getToken });

/** 发起请求并解包 Result 信封：HTTP 异常或业务码非 0 时抛错。 */
export async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const { method = 'GET', body, headers, signal } = init ?? {};
  return client.http<T>(url, {
    method: method as HttpOptions['method'],
    body,
    headers,
    signal: signal ?? undefined,
  });
}

/** 常用方法快捷封装 */
export const http = {
  get: <T>(url: string) => request<T>(url),
  post: <T>(url: string, body?: unknown) =>
    request<T>(url, { method: 'POST', body: JSON.stringify(body) }),
  put: <T>(url: string, body?: unknown) =>
    request<T>(url, { method: 'PUT', body: JSON.stringify(body) }),
  del: <T>(url: string) => request<T>(url, { method: 'DELETE' }),
};

export type { Result } from '@bluewhale/http';
