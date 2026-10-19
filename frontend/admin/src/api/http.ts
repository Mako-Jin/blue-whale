// 统一 HTTP 客户端：解析后端 { code, message, data } 响应体、注入 Bearer、401 清态

import { clearAuth, getToken } from './token';

/** 后端统一响应体，code=0 表示成功 */
export interface Result<T> {
  code: number;
  message: string;
  data: T;
}

/** 后端统一分页响应体（与 whale-common PageResult 字段一致） */
export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
}

export class ApiError extends Error {
  readonly code: number;

  constructor(code: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
  }
}

// 生产由 Nginx 同源反代；开发由 vite proxy 转发，故默认同源相对路径
const API_BASE: string = import.meta.env.VITE_API_BASE_URL ?? '';

export interface HttpOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
  body?: unknown;
  /** 是否注入 Authorization 头，默认 true */
  auth?: boolean;
  /** 查询参数（自动忽略 undefined/空字符串） */
  query?: Record<string, unknown>;
  signal?: AbortSignal;
}

function buildQuery(query?: Record<string, unknown>): string {
  if (!query) {
    return '';
  }
  const search = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') {
      return;
    }
    search.append(key, String(value));
  });
  const text = search.toString();
  return text ? `?${text}` : '';
}

export async function http<T>(path: string, options: HttpOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = true, query, signal } = options;

  const headers = new Headers();
  if (body !== undefined) {
    headers.set('Content-Type', 'application/json');
  }
  if (auth) {
    const token = getToken();
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }
  }

  const response = await fetch(`${API_BASE}${path}${buildQuery(query)}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal,
  });

  const text = await response.text();
  let payload: Result<T> | null = null;
  if (text) {
    try {
      payload = JSON.parse(text) as Result<T>;
    } catch {
      payload = null;
    }
  }

  // 401：令牌缺失/过期/已登出（黑名单命中）→ 清本地态并抛错，由路由守卫重定向登录
  if (response.status === 401) {
    clearAuth();
    throw new ApiError(401, payload?.message ?? '登录已失效，请重新登录');
  }
  if (!response.ok) {
    throw new ApiError(
      payload?.code ?? response.status,
      payload?.message ?? `请求失败（HTTP ${response.status}）`,
    );
  }
  if (payload && typeof payload.code === 'number' && payload.code !== 0) {
    throw new ApiError(payload.code, payload.message ?? '请求失败');
  }
  return (payload ? payload.data : undefined) as T;
}

export const get = <T>(path: string, query?: Record<string, unknown>) =>
  http<T>(path, { method: 'GET', query });

export const post = <T>(path: string, body?: unknown) => http<T>(path, { method: 'POST', body });

export const put = <T>(path: string, body?: unknown) => http<T>(path, { method: 'PUT', body });

export const del = <T>(path: string) => http<T>(path, { method: 'DELETE' });
