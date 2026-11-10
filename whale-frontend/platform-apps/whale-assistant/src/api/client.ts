// AI 助手子应用请求层：基于 @bluewhale/http，token 与用户从基座缓存读取
//
// 后端统一返回 `Result` 信封：{ code, message, data }，code === 0 表示成功。
// 所有请求走相对路径（如 `/assistant/sessions`），由基座 / Nginx 经网关 8080 代理，
// 网关不剥离 `/assistant` 一级前缀（REQ-ARCH-003）。

import { ApiError, createHttpClient, type HttpOptions } from '@bluewhale/http';
import { PLATFORM_USER_KEY } from '@bluewhale/whale-auth';

const USER_STORAGE_KEY = PLATFORM_USER_KEY;

/** 基座缓存的当前用户 */
export interface AsstUser {
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
export function getCurrentUser(): AsstUser | null {
  const raw = localStorage.getItem(USER_STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as AsstUser;
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

/**
 * POST 流式问答：手工解析 SSE 帧（EventSource 不支持 POST）。
 *
 * @param url      流式端点
 * @param body     请求体
 * @param onEvent  事件回调（事件名, data JSON 串）
 * @param signal   中断信号
 */
export async function postStream(
  url: string,
  body: unknown,
  onEvent: (event: string, data: string) => void,
  signal?: AbortSignal,
): Promise<void> {
  const token = getToken();
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
    signal,
  });
  if (!response.ok || !response.body) {
    throw new ApiError(response.status, `流式问答失败：HTTP ${response.status}`);
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let event = 'message';
  let data = '';
  for (;;) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    let index = buffer.indexOf('\n');
    while (index >= 0) {
      const line = buffer.slice(0, index).replace(/\r$/, '');
      buffer = buffer.slice(index + 1);
      if (line === '') {
        if (data) {
          onEvent(event, data);
          event = 'message';
          data = '';
        }
      } else if (line.startsWith('event:')) {
        event = line.slice(6).trim();
      } else if (line.startsWith('data:')) {
        data = data ? `${data}\n${line.slice(5).trimStart()}` : line.slice(5).trimStart();
      }
      index = buffer.indexOf('\n');
    }
  }
  if (data) {
    onEvent(event, data);
  }
}

export type { PageResult, Result } from '@bluewhale/http';
export { ApiError };
