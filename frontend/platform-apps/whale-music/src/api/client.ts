// 音乐子应用请求层：基于 @bluewhale/http，token 与用户从基座缓存读取
//
// 后端统一返回 `Result` 信封：{ code, message, data }，code === 0 表示成功。
// 所有请求走相对路径（如 `/music/public/song/page`），由基座 / Nginx 经网关 8080 代理。

import { ApiError, createHttpClient, type HttpOptions } from '@bluewhale/http';

/** 基座用户缓存 key */
import { PLATFORM_USER_KEY } from '@bluewhale/whale-auth';

const USER_STORAGE_KEY = PLATFORM_USER_KEY;

/** 基座缓存的当前用户 */
export interface MusicUser {
  userId?: number | string;
  username?: string;
  nickname?: string;
  permissions?: string[];
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
export function getCurrentUser(): MusicUser | null {
  const raw = localStorage.getItem(USER_STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as MusicUser;
  } catch {
    return null;
  }
}

/** 读取当前用户权限码（用于入口显隐） */
export function getPermissions(): string[] {
  return getCurrentUser()?.permissions ?? [];
}

/** 判断是否拥有权限码（无缓存时返回 false） */
export function hasPermission(code: string): boolean {
  return getPermissions().includes(code);
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

/** 上传表单（multipart/form-data），不能走 JSON 请求层。 */
export async function upload<T>(url: string, data: FormData): Promise<T> {
  const token = getToken();
  const response = await fetch(url, {
    method: 'POST',
    body: data,
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    credentials: 'same-origin',
  });
  const text = await response.text();
  let payload: { code?: number; message?: string; data?: T } | null = null;
  try {
    payload = text ? (JSON.parse(text) as { code?: number; message?: string; data?: T }) : null;
  } catch {
    payload = null;
  }
  if (!response.ok) {
    throw new ApiError(payload?.code ?? response.status, payload?.message ?? `请求失败（HTTP ${response.status}）`);
  }
  if (payload && typeof payload.code === 'number' && payload.code !== 0) {
    throw new ApiError(payload.code, payload.message ?? '请求失败');
  }
  return payload?.data as T;
}

export type { PageResult, Result } from '@bluewhale/http';
