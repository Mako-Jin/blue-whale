// PDM 子应用请求层：基于 @bluewhale/http，token 从基座通过 qiankun props 注入
//
// 后端统一返回 `Result` 信封：{ code, message, data }，code === 0 表示成功。
// 所有请求走相对路径（如 `/pdm/v1/document/page`），由基座 / Nginx 经网关 8080 代理。

import { createHttpClient, type DownloadResult, type HttpOptions } from '@bluewhale/http';

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

const client = createHttpClient({ getToken });

/** 请求参数（body 允许对象 / 字符串 / FormData，统一由 @bluewhale/http 序列化） */
interface RequestOptions {
  method?: HttpOptions['method'];
  body?: unknown;
  headers?: HeadersInit;
  signal?: AbortSignal;
}

/** 发起请求并解包 Result 信封：成功返回 data，HTTP 异常或业务码非 0 时抛错。 */
export async function request<T>(url: string, init?: RequestOptions): Promise<T> {
  const { method = 'GET', body, headers, signal } = init ?? {};
  return client.http<T>(url, {
    method,
    body,
    headers,
    signal: signal ?? undefined,
  });
}

/** 常用方法快捷封装（GET 支持查询参数，空值自动忽略；对象请求体由 @bluewhale/http 序列化） */
export const http = {
  get: <T>(url: string, query?: Record<string, unknown>) => client.get<T>(url, query),
  post: <T>(url: string, body?: unknown) => request<T>(url, { method: 'POST', body }),
  put: <T>(url: string, body?: unknown) => request<T>(url, { method: 'PUT', body }),
  del: <T>(url: string) => request<T>(url, { method: 'DELETE' }),
};

/** 上传 multipart/form-data：FormData 由 @bluewhale/http 原样透传（不手写 Content-Type） */
export function upload<T>(url: string, form: FormData): Promise<T> {
  return request<T>(url, { method: 'POST', body: form });
}

/** 下载原始字节流（文件版本内容 / 导出），返回 blob 与 Content-Disposition 文件名 */
export function download(url: string, query?: Record<string, unknown>): Promise<DownloadResult> {
  return client.downloadBlob(url, query);
}

export type { DownloadResult, PageResult, Result } from '@bluewhale/http';
