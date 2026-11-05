// MES 请求层：基于 @bluewhale/http（Bearer 注入、401 静默续期、SSE 与下载能力）
//
// 后端统一返回 `Result` 信封：{ code, message, data }，
//   code === 0 表示成功，其余为业务错误码；解包后直接返回 data。
// 前端统一使用相对路径请求（如 `/mes/work-order/page`），
//   由 dev server / 生产 Nginx 经网关 8080 代理到对应后端服务。
//
// 鉴权：网关对 /mes/** 强制 JWT（Authorization: Bearer）。
//   开发态子应用与基座不同源、localStorage 不共享，
//   基座经 qiankun props 注入 getToken（读基座内存令牌），
//   子应用在 mount 时经 configureAuthToken 接线（见 main.tsx）。

import { createHttpClient } from '@bluewhale/http';

/** 基座经 qiankun props 注入的令牌读取器 */
let tokenProvider: (() => string | null | undefined) | null = null;

/** 接线/解除基座令牌读取器（qiankun mount/unmount 调用） */
export function configureAuthToken(provider: (() => string | null | undefined) | null): void {
  tokenProvider = provider;
}

export const client = createHttpClient({
  getToken: () => tokenProvider?.() ?? null,
});

/** 发起请求并解包 Result 信封：成功返回 data，HTTP 异常或业务码非 0 时抛错。 */
export async function request<T>(url: string, init?: RequestInit): Promise<T> {
  return client.request<T>(url, init as never);
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
