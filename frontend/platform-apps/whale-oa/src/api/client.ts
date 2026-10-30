// OA 子应用请求层：基于 @bluewhale/http，保留原 request(url, init) 语义
//
// 后端统一返回 `Result` 信封：{ code, message, data }，code === 0 表示成功。
// token 从基座注入的读取器获取（V-03：基座 accessToken 为内存态，经 qiankun
// props 共享，不再有 token 的 localStorage），请求自动带 Authorization。
// 前端统一使用相对路径（如 `/oa/leave/page`），由基座 / Nginx 经网关 8080 代理。

import { createHttpClient, type HttpOptions } from '@bluewhale/http';

/** 基座用户缓存 key */
import { PLATFORM_USER_KEY } from '@bluewhale/whale-auth';

const USER_STORAGE_KEY = PLATFORM_USER_KEY;

/** 基座缓存的当前用户（仅取表单默认值所需字段） */
export interface OaUser {
  userId?: number | string;
  username?: string;
  nickname?: string;
}

/** 基座注入的登录态读取器（qiankun props；独立运行时为 null） */
let hostGetToken: (() => string | null) | null = null;

/** 在 qiankun mount 前保存基座注入的登录态读取器（unmount 后清空） */
export function setPlatformHostProps(props?: { getToken?: () => string | null } | null): void {
  hostGetToken = props?.getToken ?? null;
}

/** 读取当前登录 token（未登录时为 null） */
export function getToken(): string | null {
  return hostGetToken?.() ?? null;
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
