// OA 子应用请求层：基于 @bluewhale/http，保留原 request(url, init) 语义
//
// 后端统一返回 `Result` 信封：{ code, message, data }，code === 0 表示成功。
// token 与当前用户从基座缓存读取（qiankun 与基座同源），请求自动带 Authorization。
// 前端统一使用相对路径（如 `/oa/leave/page`），由基座 / Nginx 经网关 8080 代理。

import { createHttpClient, type HttpOptions } from '@bluewhale/http';

/** 基座 token 存储 key（对齐 whale-platform/src/api/token.ts） */
const TOKEN_STORAGE_KEY = 'whale_platform_access_token';

/** 基座用户缓存 key */
const USER_STORAGE_KEY = 'whale_platform_user';

/** 基座缓存的当前用户（仅取表单默认值所需字段） */
export interface OaUser {
  userId?: number | string;
  username?: string;
  nickname?: string;
}

/** 读取当前登录 token（未登录时为 null） */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_STORAGE_KEY);
}

/** 读取基座缓存的当前用户（无缓存或解析失败时为 null） */
export function getCurrentUser(): OaUser | null {
  const raw = localStorage.getItem(USER_STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as OaUser;
  } catch {
    return null;
  }
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
