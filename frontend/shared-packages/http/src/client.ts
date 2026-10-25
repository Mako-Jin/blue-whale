// 前端统一请求层：信封解包、Bearer 注入、401 单飞续期重放、文件下载、SSE 流式请求
//
// 注意：本包是纯库构建产物，禁止使用 import.meta.env（tsup 库构建会失败），
// baseUrl 必须由各应用在 createHttpClient 时传入。

import { AppEvents, eventBus } from '@bluewhale/whale-common-tools';

/** 后端统一响应体，code=0 表示成功 */
export interface Result<T = unknown> {
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

export interface HttpOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
  body?: unknown;
  /** 是否注入 Authorization 头，默认 true */
  auth?: boolean;
  /** 查询参数（自动忽略 undefined/空字符串） */
  query?: Record<string, unknown>;
  /** 额外请求头（供 request(url, init) 兼容入口透传调用方 headers） */
  headers?: HeadersInit;
  signal?: AbortSignal;
}

/** 文件下载结果：原始字节流 + 从 Content-Disposition 解析出的文件名 */
export interface DownloadResult {
  blob: Blob;
  filename: string;
}

/** SSE 事件回调：event 为事件名（meta/delta/done/error），data 为已解析的 JSON（解析失败时为原始文本） */
export type SseEventHandler = (event: string, data: unknown) => void;

/** 刷新接口返回的登录数据；各应用可传入更完整的 LoginResponse 作为泛型参数 */
export interface LoginData {
  accessToken: string;
  refreshToken?: string;
  tokenType?: string;
  expiresIn?: number;
  username?: string;
  nickname?: string;
  roles?: string[];
}

export interface HttpClientConfig<TLogin extends LoginData = LoginData> {
  /** 后端基地址；生产同源反代、开发 vite proxy，通常传空串 */
  baseUrl?: string;
  /** 读取 accessToken；未提供时不注入 Authorization（如 erp 等无鉴权应用） */
  getToken?: () => string | null;
  /** 读取 refreshToken；与 saveLogin 同时提供才启用自动续期 */
  getRefreshToken?: () => string | null;
  /**
   * 读取当前租户 ID；提供后所有请求注入 X-Tenant-Id。
   * 仅用于展示与兼容：服务端以 JWT 的 tenantId claim 为准，网关会清除入站值并重新注入。
   */
  getTenantId?: () => string | number | null | undefined;
  /** 刷新成功后落盘新令牌 */
  saveLogin?: (data: TLogin) => void;
  /** 401 且无法续期时清理本地登录态 */
  clearAuth?: () => void;
  /** 401 且无法续期时的额外动作（如跳转登录页） */
  onUnauthorized?: () => void;
}

/** 不做自动续期的路径：登录/刷新自身的 401 直接透传，避免递归续期 */
const NO_REFRESH_PATHS = new Set(['/auth/login', '/auth/refresh']);

/** 解析不到 Content-Disposition 时的兜底文件名 */
const FALLBACK_FILENAME = 'download';

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
 * 请求体序列化：对象走 JSON；已是字符串的直接透传
 * （门户/骨架应用的 request(url, init) 会传入已序列化的请求体，避免二次编码）。
 */
function serializeBody(body: unknown): string | undefined {
  if (body === undefined) {
    return undefined;
  }
  return typeof body === 'string' ? body : JSON.stringify(body);
}

/**
 * 生成 32 位十六进制链路 ID：优先 crypto.randomUUID 去横线，
 * 无 crypto 的环境（老浏览器/非标准运行时）用「时间戳 + 随机数」兜底。
 */
function createTraceId(): string {
  const cryptoObj = typeof globalThis === 'undefined' ? undefined : globalThis.crypto;
  if (cryptoObj && typeof cryptoObj.randomUUID === 'function') {
    return cryptoObj.randomUUID().replace(/-/g, '');
  }
  const time = Date.now().toString(16).padStart(8, '0');
  let random = '';
  while (random.length < 24) {
    random += Math.floor(Math.random() * 0x100000000)
      .toString(16)
      .padStart(8, '0');
  }
  return `${time}${random.slice(0, 24)}`;
}

/**
 * 从 Content-Disposition 响应头解析文件名（形如 attachment; filename="xxx.csv"）。
 * 优先取引号内的值，其次取分号前的裸值；对 decodeURIComponent 做 try/catch 回退。
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

/**
 * 创建应用级 HTTP 客户端。
 *
 * <p>未同时提供 getRefreshToken 与 saveLogin 时不启用自动续期（ops/portal/erp 保持原行为）；
 * 每个请求（含刷新、下载、SSE）都会自动携带 32 位十六进制 X-Trace-Id。</p>
 */
export function createHttpClient<TLogin extends LoginData = LoginData>(
  config: HttpClientConfig<TLogin> = {},
) {
  const baseUrl = config.baseUrl ?? '';
  // 只有同时提供读取 refreshToken 与落盘回调，才启用自动续期
  const canRefresh = Boolean(config.getRefreshToken && config.saveLogin);

  /** 单飞刷新中的 Promise：并发 401 合并为同一次刷新请求 */
  let refreshPromise: Promise<boolean> | null = null;

  /**
   * 构造基础请求头并注入链路 ID。
   * 配置了 getTenantId 时同时注入 X-Tenant-Id（服务端仍以 JWT 租户声明为准）。
   */
  function newHeaders(): Headers {
    const headers = new Headers();
    headers.set('X-Trace-Id', createTraceId());
    const tenantId = config.getTenantId?.();
    if (tenantId !== undefined && tenantId !== null && tenantId !== '') {
      headers.set('X-Tenant-Id', String(tenantId));
    }
    return headers;
  }

  /** 401 且续期失败：清理本地登录态并执行应用侧回调（如跳登录页），随后抛出 ApiError */
  function handleUnauthorized(message?: string): never {
    config.clearAuth?.();
    config.onUnauthorized?.();
    throw new ApiError(401, message ?? '登录已失效，请重新登录');
  }

  /**
   * 刷新会话（一次一换）：POST /auth/refresh 换取新令牌，并整体替换本地双 token。
   * 刷新成功后发放新 refreshToken，旧 refreshToken 在服务端随之失效。
   *
   * @returns 新的登录响应
   * @throws ApiError 无 refreshToken 或刷新失败时抛出 401
   */
  async function refreshSession(): Promise<TLogin> {
    const refreshToken = config.getRefreshToken?.();
    if (!refreshToken) {
      throw new ApiError(401, '无可用 refreshToken，请重新登录');
    }
    const headers = newHeaders();
    headers.set('Content-Type', 'application/json');
    const response = await fetch(`${baseUrl}/auth/refresh`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ refreshToken }),
    });
    const payload = parseResult<TLogin>(await response.text());
    if (response.status !== 200 || !payload || payload.code !== 0 || !payload.data?.accessToken) {
      throw new ApiError(payload?.code ?? 401, payload?.message ?? '登录已失效，请重新登录');
    }
    config.saveLogin?.(payload.data);
    // 审计：令牌自动续期成功留痕（只记录触发原因，不落任何令牌值）
    eventBus.emit(AppEvents.AUTH_TOKEN_REFRESH, { trigger: '401' });
    return payload.data;
  }

  /** 单飞续期：并发 401 共享同一次刷新请求；返回是否刷新成功 */
  function refreshOnce(): Promise<boolean> {
    if (!canRefresh) {
      return Promise.resolve(false);
    }
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

  const http = <T>(path: string, options: HttpOptions = {}): Promise<T> =>
    send<T>(path, options, true);

  /**
   * 发送请求并按需续期重放。
   *
   * @param path 请求路径（同源相对路径）
   * @param options 请求选项
   * @param allowRefresh 是否允许在 401 时自动续期并重放（重放仅一次，防递归）
   * @returns 解析后的业务数据
   */
  async function send<T>(path: string, options: HttpOptions, allowRefresh: boolean): Promise<T> {
    const { method = 'GET', body, auth = true, query, headers: extraHeaders, signal } = options;

    const headers = newHeaders();
    // 调用方显式传入的头优先（兼容 request(url, init) 旧语义）
    if (extraHeaders) {
      new Headers(extraHeaders).forEach((value, key) => headers.set(key, value));
    }
    if (body !== undefined) {
      headers.set('Content-Type', 'application/json');
    }
    if (auth) {
      const token = config.getToken?.();
      if (token) {
        headers.set('Authorization', `Bearer ${token}`);
      }
    }

    const response = await fetch(`${baseUrl}${path}${buildQuery(query)}`, {
      method,
      headers,
      body: serializeBody(body),
      signal,
    });

    const payload = parseResult<T>(await response.text());

    // 401：令牌缺失/过期/已登出（黑名单命中）→ 先静默续期并重放一次，仍失败则清态抛出
    if (response.status === 401) {
      if (allowRefresh && !NO_REFRESH_PATHS.has(path) && (await refreshOnce())) {
        return send<T>(path, options, false);
      }
      handleUnauthorized(payload?.message);
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

  /**
   * 下载原始字节流（导出等接口）：响应体不是 { code, message, data } 信封，故不复用 send()。
   * 401 处理与 send() 一致：先静默续期并重放一次（仅一次，防递归），仍失败则清态抛出。
   */
  async function downloadBlob(
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
    const headers = newHeaders();
    const token = config.getToken?.();
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }

    const response = await fetch(`${baseUrl}${path}${buildQuery(query)}`, {
      method: 'GET',
      headers,
    });

    // 401：令牌缺失/过期/已登出 → 先续期并重放一次，仍失败则清态抛出
    if (response.status === 401) {
      if (allowRefresh && !NO_REFRESH_PATHS.has(path) && (await refreshOnce())) {
        return fetchBlob(path, query, false);
      }
      const payload = parseResult<unknown>(await response.clone().text());
      handleUnauthorized(payload?.message);
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

  /**
   * 发起 SSE 流式请求（POST，JSON 请求体，text/event-stream 响应）。
   * 按 SSE 帧（空行分隔）解析 event / 多行 data；401 与普通请求一致先静默续期并重放一次。
   */
  async function streamSse(
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
    const headers = newHeaders();
    headers.set('Content-Type', 'application/json');
    headers.set('Accept', 'text/event-stream');
    const token = config.getToken?.();
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }

    const response = await fetch(`${baseUrl}${path}`, {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
      signal,
    });

    if (response.status === 401) {
      if (allowRefresh && !NO_REFRESH_PATHS.has(path) && (await refreshOnce())) {
        return sendSse(path, body, onEvent, signal, false);
      }
      handleUnauthorized();
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

  return {
    http,
    get: <T>(path: string, query?: Record<string, unknown>) =>
      http<T>(path, { method: 'GET', query }),
    post: <T>(path: string, body?: unknown) => http<T>(path, { method: 'POST', body }),
    put: <T>(path: string, body?: unknown) => http<T>(path, { method: 'PUT', body }),
    del: <T>(path: string) => http<T>(path, { method: 'DELETE' }),
    request: http,
    refreshSession,
    downloadBlob,
    streamSse,
  };
}
