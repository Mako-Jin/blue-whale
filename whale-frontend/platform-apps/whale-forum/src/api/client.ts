// 论坛子应用请求层：基于 @bluewhale/http，token 与用户从基座缓存读取
//
// 后端统一返回 `Result` 信封：{ code, message, data }，code === 0 表示成功。
// 所有请求走相对路径（如 `/forum/meta`），由基座 / Nginx 经网关 8080 代理，
// 网关不剥离 `/forum` 一级前缀（REQ-ARCH-003）——路径写错不会编译报错，
// 只在点开页面时 404，故前后端路径必须逐字对齐（check-api-path-drift.py 守护）。

import { createHttpClient, type HttpOptions } from '@bluewhale/http';
import { PLATFORM_USER_KEY } from '@bluewhale/whale-auth';

const USER_STORAGE_KEY = PLATFORM_USER_KEY;

/** 基座缓存的当前用户 */
export interface ForumUser {
  userId?: string;
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
export function getCurrentUser(): ForumUser | null {
  const raw = localStorage.getItem(USER_STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as ForumUser;
  } catch {
    return null;
  }
}

/** 判断是否拥有权限码（无缓存时返回 false） */
export function hasPermission(code: string): boolean {
  return (getCurrentUser()?.permissions ?? []).includes(code);
}

const client = createHttpClient({ getToken });

/** 本子应用请求参数：body 允许直接传对象（共享客户端统一 JSON 序列化并补 Content-Type） */
export interface ForumRequestInit {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  headers?: HeadersInit;
  signal?: AbortSignal;
}

/** 发起请求并解包 Result 信封：HTTP 异常或业务码非 0 时抛错。 */
export async function request<T>(url: string, init?: ForumRequestInit): Promise<T> {
  const { method = 'GET', body, headers, signal } = init ?? {};
  return client.http<T>(url, {
    method: method as HttpOptions['method'],
    body,
    headers,
    signal: signal ?? undefined,
  });
}
