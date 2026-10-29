// 合同子应用请求层：基于 @bluewhale/http，token 与用户从基座缓存读取
//
// 后端统一返回 `Result` 信封：{ code, message, data }，code === 0 表示成功。
// 所有请求走相对路径（如 `/contract/page`），由基座 / Nginx 经网关 8080 代理。

import { createHttpClient, type HttpOptions } from '@bluewhale/http';

/** 基座 token 存储 key（对齐 whale-platform/src/api/token.ts） */
import { PLATFORM_ACCESS_TOKEN_KEY, PLATFORM_USER_KEY } from '@bluewhale/whale-auth';

const TOKEN_STORAGE_KEY = PLATFORM_ACCESS_TOKEN_KEY;

/** 基座用户缓存 key */
const USER_STORAGE_KEY = PLATFORM_USER_KEY;

/** 基座缓存的当前用户 */
export interface ContractUser {
  userId?: number | string;
  username?: string;
  nickname?: string;
  permissions?: string[];
}

/** 读取当前登录 token（未登录时为 null） */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_STORAGE_KEY);
}

/** 读取基座缓存的当前用户（无缓存或解析失败时为 null） */
export function getCurrentUser(): ContractUser | null {
  const raw = localStorage.getItem(USER_STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as ContractUser;
  } catch {
    return null;
  }
}

/** 读取当前用户权限码（用于按钮显隐） */
export function getPermissions(): string[] {
  return getCurrentUser()?.permissions ?? [];
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

/** 常用方法快捷封装（GET 支持查询参数，空值自动忽略） */
export const http = {
  get: <T>(url: string, query?: Record<string, unknown>) => client.get<T>(url, query),
  post: <T>(url: string, body?: unknown) =>
    request<T>(url, { method: 'POST', body: JSON.stringify(body) }),
  put: <T>(url: string, body?: unknown) =>
    request<T>(url, { method: 'PUT', body: JSON.stringify(body) }),
  del: <T>(url: string) => request<T>(url, { method: 'DELETE' }),
};

/** 下载二进制响应（图片等非 Result 信封接口），失败时尽力解析错误信息。 */
export async function requestBlob(url: string): Promise<Blob> {
  const token = getToken();
  const response = await fetch(url, {
    method: 'GET',
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    credentials: 'same-origin',
  });
  if (!response.ok) {
    let message = `请求失败（HTTP ${response.status}）`;
    try {
      const text = await response.text();
      const body = JSON.parse(text) as { message?: string };
      if (body.message) {
        message = body.message;
      }
    } catch {
      // 非 JSON 错误体时保留默认提示
    }
    throw new Error(message);
  }
  return response.blob();
}

export type { PageResult, Result } from '@bluewhale/http';
