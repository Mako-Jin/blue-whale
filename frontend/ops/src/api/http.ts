// 统一 HTTP 客户端：注入 Bearer、信封解包（HTTP 恒 200，业务码在 body.code）
// code=0 成功 → 返回 body.data；code!==0 → 抛 Error(body.message)；真实 HTTP 401 → 清 token 跳登录页

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

// 生产由 Nginx 同源反代；开发由 vite proxy 转发，故默认同源相对路径
const API_BASE: string = import.meta.env.VITE_API_BASE_URL ?? '';

export interface HttpOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  /** 是否注入 Authorization 头，默认 true */
  auth?: boolean;
  /** 查询参数（自动忽略 undefined/null/空字符串） */
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

/** 把响应文本解析为统一响应体；非 JSON 文本返回 null */
function parseResult<T>(text: string): Result<T> | null {
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text) as Result<T>;
  } catch {
    return null;
  }
}

/** 未登录/令牌失效时清态并跳转登录页 */
function redirectToLogin(): void {
  clearAuth();
  const { pathname, search, hash } = window.location;
  if (pathname !== '/login') {
    const from = encodeURIComponent(`${pathname}${search}${hash}`);
    window.location.href = `/login?from=${from}`;
  }
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

  // 真实 HTTP 401：令牌缺失/过期/已登出 → 清 token 并跳登录页
  if (response.status === 401) {
    redirectToLogin();
    throw new Error('登录已失效，请重新登录');
  }

  const payload = parseResult<T>(await response.text());

  // 非 2xx（网关/服务异常）直接按 HTTP 语义抛错
  if (!response.ok) {
    throw new Error(payload?.message ?? `请求失败（HTTP ${response.status}）`);
  }

  // 信封解包：HTTP 恒 200，业务码在 body.code
  if (!payload) {
    throw new Error('响应体解析失败');
  }
  if (payload.code !== 0) {
    throw new Error(payload.message ?? '请求失败');
  }
  return payload.data;
}

export const get = <T>(path: string, query?: Record<string, unknown>) =>
  http<T>(path, { method: 'GET', query });

export const post = <T>(path: string, body?: unknown) => http<T>(path, { method: 'POST', body });

export const put = <T>(path: string, body?: unknown) => http<T>(path, { method: 'PUT', body });

export const del = <T>(path: string) => http<T>(path, { method: 'DELETE' });
