// 统一 HTTP 客户端：解析后端 { code, message, data } 响应体、注入 Bearer、401 自动续期、401 清态
// 复用 workflow-designer 版实现，并新增 SSE 流式请求（POST /agent/chat）

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

/** SSE 事件回调：event 为事件名（meta/delta/done/error），data 为已解析的 JSON（解析失败时为原始文本） */
export type SseEventHandler = (event: string, data: unknown) => void;

/**
 * 发起 SSE 流式请求（POST，JSON 请求体，text/event-stream 响应）。
 *
 * <p>按 SSE 帧（空行分隔）解析 {@code event:} / 多行 {@code data:}；
 * 401 与普通请求一致先静默续期并重放一次；客户端可通过 AbortSignal 中止流。</p>
 *
 * @param path 请求路径（同源相对路径）
 * @param body 请求体
 * @param onEvent 事件回调
 * @param signal 中止信号
 */
export async function streamSse(
  path: string,
  body: unknown,
  onEvent: SseEventHandler,
  signal?: AbortSignal,
): Promise<void> {
  return sendSse(path, body, onEvent, signal, true);
}

/** SSE 请求实际发送逻辑（allowRefresh 控制 401 时是否续期重放一次） */
async function sendSse(
  path: string,
  body: unknown,
  onEvent: SseEventHandler,
  signal: AbortSignal | undefined,
  allowRefresh: boolean,
): Promise<void> {
  const headers = new Headers();
  headers.set('Content-Type', 'application/json');
  headers.set('Accept', 'text/event-stream');
  const token = getToken();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
    signal,
  });

  if (response.status === 401) {
    if (allowRefresh && !NO_REFRESH_PATHS.has(path) && (await refreshOnce())) {
      return sendSse(path, body, onEvent, signal, false);
    }
    clearAuth();
    throw new ApiError(401, '登录已失效，请重新登录');
  }
  if (!response.ok) {
    const payload = parseResult<unknown>(await response.text());
    throw new ApiError(
      payload?.code ?? response.status,
      payload?.message ?? `请求失败（HTTP ${response.status}）`,
    );
  }
  if (!response.body) {
    throw new ApiError(0, '当前浏览器不支持流式响应');
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';
  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) {
        break;
      }
      buffer += decoder.decode(value, { stream: true });
      // 统一换行并切出完整帧（空行分隔）
      buffer = buffer.replace(/\r\n/g, '\n');
      let boundary = buffer.indexOf('\n\n');
      while (boundary >= 0) {
        const frame = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        dispatchFrame(frame, onEvent);
        boundary = buffer.indexOf('\n\n');
      }
    }
    buffer += decoder.decode();
    if (buffer.trim()) {
      dispatchFrame(buffer.replace(/\r\n/g, '\n'), onEvent);
    }
  } finally {
    reader.releaseLock();
  }
}

/** 解析单帧 SSE：提取 event 名与多行 data，data 优先按 JSON 解析 */
function dispatchFrame(frame: string, onEvent: SseEventHandler): void {
  let event = 'message';
  const dataLines: string[] = [];
  frame.split('\n').forEach((line) => {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).replace(/^ /, ''));
    }
  });
  if (dataLines.length === 0) {
    return;
  }
  const raw = dataLines.join('\n');
  let data: unknown = raw;
  try {
    data = JSON.parse(raw);
  } catch {
    // 非 JSON 数据保持原始文本
  }
  onEvent(event, data);
}
