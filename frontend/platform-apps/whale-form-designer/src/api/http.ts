// 统一 HTTP 客户端：解析后端 { code, message, data } 响应体、注入 Bearer、401 自动续期、401 清态
// 复用 admin 版实现，token 读写引用本应用 token.ts

import { clearAuth, getRefreshToken, getToken, saveLogin, type LoginResponse } from './token';

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

/** 不做自动续期的路径：登录/刷新自身的 401 直接透传，避免递归续期 */
const NO_REFRESH_PATHS = new Set(['/auth/login', '/auth/refresh']);

/** 单飞刷新中的 Promise：并发 401 合并为同一次刷新请求 */
let refreshPromise: Promise<boolean> | null = null;

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

/**
 * 刷新会话（一次一换）：POST /auth/refresh 换取新令牌，并整体替换本地双 token。
 * 刷新成功后发放新 refreshToken，旧 refreshToken 在服务端随之失效。
 *
 * @returns 新的登录响应
 * @throws ApiError 无 refreshToken 或刷新失败时抛出 401
 */
export async function refreshSession(): Promise<LoginResponse> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    throw new ApiError(401, '无可用 refreshToken，请重新登录');
  }
  const response = await fetch(`${API_BASE}/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  });
  const payload = parseResult<LoginResponse>(await response.text());
  if (response.status !== 200 || !payload || payload.code !== 0 || !payload.data?.accessToken) {
    throw new ApiError(payload?.code ?? 401, payload?.message ?? '登录已失效，请重新登录');
  }
  saveLogin(payload.data);
  return payload.data;
}

/** 单飞续期：并发 401 共享同一次刷新请求；返回是否刷新成功 */
function refreshOnce(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = refreshSession()
      .then(() => true)
      .catch(() => false)
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

export async function http<T>(path: string, options: HttpOptions = {}): Promise<T> {
  return send<T>(path, options, true);
}

/**
 * 发送请求并按需续期重放。
 *
 * @param path 请求路径（同源相对路径）
 * @param options 请求选项
 * @param allowRefresh 是否允许在 401 时自动续期并重放（重放仅一次，防递归）
 * @returns 解析后的业务数据
 */
async function send<T>(path: string, options: HttpOptions, allowRefresh: boolean): Promise<T> {
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

  const payload = parseResult<T>(await response.text());

  // 401：令牌缺失/过期/已登出（黑名单命中）→ 先静默续期并重放一次，仍失败则清态抛出
  if (response.status === 401) {
    if (allowRefresh && !NO_REFRESH_PATHS.has(path) && (await refreshOnce())) {
      return send<T>(path, options, false);
    }
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

/** 文件下载结果：原始字节流 + 从 Content-Disposition 解析出的文件名 */
export interface DownloadResult {
  blob: Blob;
  filename: string;
}

/** 解析失败时的兜底文件名（无 Content-Disposition 或取不到 filename 时使用） */
const FALLBACK_FILENAME = 'download';

/**
 * 从 Content-Disposition 响应头解析文件名（形如 attachment; filename="xxx.csv"）。
 * 优先取引号内的值，其次取分号前的裸值；对 decodeURIComponent 做 try/catch 回退。
 *
 * @param disposition Content-Disposition 响应头原文
 * @returns 解析出的文件名，解析不到时返回兜底名
 */
function parseFilename(disposition: string | null): string {
  if (!disposition) {
    return FALLBACK_FILENAME;
  }
  const matched = /filename="([^"]+)"/i.exec(disposition) ?? /filename=([^;]+)/i.exec(disposition);
  const raw = matched?.[1]?.trim();
  if (!raw) {
    return FALLBACK_FILENAME;
  }
  try {
    return decodeURIComponent(raw);
  } catch {
    return raw;
  }
}

/**
 * 下载原始字节流（导出等接口）：响应体不是 { code, message, data } 信封，故不复用 http<T>。
 * 401 处理与 send() 一致：先静默续期并重放一次（仅一次，防递归），仍失败则清态抛出；
 * 非 2xx 时尽力把响应体按信封解析取 message，取不到则回退「请求失败（HTTP xxx）」。
 *
 * @param path 请求路径（同源相对路径）
 * @param query 查询参数（走 buildQuery，自动忽略 undefined/空字符串）
 * @returns 解析后的文件 Blob 与文件名
 * @throws ApiError 续期失败仍 401，或响应非 2xx 时抛出
 */
export async function downloadBlob(
  path: string,
  query?: Record<string, unknown>,
): Promise<DownloadResult> {
  return fetchBlob(path, query, true);
}

/** 下载请求的实际发送逻辑（allowRefresh 控制 401 时是否允许续期重放一次） */
async function fetchBlob(
  path: string,
  query: Record<string, unknown> | undefined,
  allowRefresh: boolean,
): Promise<DownloadResult> {
  const headers = new Headers();
  const token = getToken();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(`${API_BASE}${path}${buildQuery(query)}`, {
    method: 'GET',
    headers,
  });

  // 401：令牌缺失/过期/已登出 → 先续期并重放一次，仍失败则清态抛出
  if (response.status === 401) {
    if (allowRefresh && !NO_REFRESH_PATHS.has(path) && (await refreshOnce())) {
      return fetchBlob(path, query, false);
    }
    clearAuth();
    const payload = parseResult<unknown>(await response.clone().text());
    throw new ApiError(401, payload?.message ?? '登录已失效，请重新登录');
  }
  if (!response.ok) {
    const payload = parseResult<unknown>(await response.clone().text());
    throw new ApiError(
      payload?.code ?? response.status,
      payload?.message ?? `请求失败（HTTP ${response.status}）`,
    );
  }

  const blob = await response.blob();
  return { blob, filename: parseFilename(response.headers.get('Content-Disposition')) };
}
