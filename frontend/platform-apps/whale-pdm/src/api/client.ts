// 极简请求层：基于 @bluewhale/http，供后续接入后端接口（无鉴权）
//
// 后端统一返回 `Result` 信封：{ code, message, data }，
//   code === 0 表示成功，其余为业务错误码；解包后直接返回 data。
// 前端统一使用相对路径请求（如 `/pdm/product/list`），
//   由 dev server / 生产 Nginx 经网关 8080 代理到对应后端服务。

import { createHttpClient, type HttpOptions } from '@bluewhale/http';

const client = createHttpClient();

/** 发起请求并解包 Result 信封：成功返回 data，HTTP 异常或业务码非 0 时抛错。 */
export async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const { method = 'GET', body, signal } = init ?? {};
  return client.http<T>(url, {
    method: method as HttpOptions['method'],
    body,
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
