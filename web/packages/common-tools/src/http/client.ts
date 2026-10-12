import { logger as rootLogger } from '../logger';
import type { Logger } from '../logger';
import {
  AbortError,
  BusinessError,
  HttpError,
  HttpStatusError,
  NetworkError,
  ParseError,
  TimeoutError,
} from './errors';
import type {
  ApiEnvelope,
  HttpClient,
  HttpConfig,
  HttpMethod,
  RequestContext,
  RequestOptions,
  QueryValue,
  UnauthorizedContext,
} from './types';

const DEFAULT_TIMEOUT = 15_000;
const DEFAULT_SUCCESS_CODE = 0;

/** 判断响应体是否符合统一响应结构。只看 `code` 与 `data`：message/timestamp 缺失不影响解包 */
function isEnvelope(value: unknown): value is ApiEnvelope<unknown> {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as { code?: unknown }).code === 'number' &&
    'data' in (value as Record<string, unknown>)
  );
}

/** 仅保留有值的查询参数 */
function buildQuery(query?: Record<string, QueryValue>): string {
  if (!query) return '';
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') continue;
    params.append(key, String(value));
  }
  const text = params.toString();
  return text ? `?${text}` : '';
}

/** 拼接基地址与路径；绝对 URL 原样使用 */
function joinUrl(baseUrl: string, path: string): string {
  if (/^https?:\/\//i.test(path)) return path;
  if (!baseUrl) return path;
  return `${baseUrl.replace(/\/+$/, '')}/${path.replace(/^\/+/, '')}`;
}

/** 不区分大小写地查找已有请求头，避免出现 `content-type` 与 `Content-Type` 并存 */
function findHeaderKey(headers: Record<string, string>, name: string): string | undefined {
  const lower = name.toLowerCase();
  return Object.keys(headers).find((key) => key.toLowerCase() === lower);
}

/** 尽力从响应体里取出可读文案 */
function extractMessage(payload: unknown): string | undefined {
  if (typeof payload === 'string' && payload.trim()) return payload.trim().slice(0, 200);
  if (typeof payload === 'object' && payload !== null) {
    const message = (payload as { message?: unknown }).message;
    if (typeof message === 'string' && message.trim()) return message;
  }
  return undefined;
}

async function readBody(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    // 非 JSON 的响应体（网关错误页、纯文本）原样返回，由上层决定怎么呈现
    return text;
  }
}

/**
 * 创建一个请求客户端。
 *
 * <p><b>这个文件刻意不认识任何业务接口。</b>它只负责"怎么把一次请求发出去、怎么把响应翻译成结果或错误"；
 * 具体的接口路径与出入参类型由契约生成（见 `packages/common-api`）。
 * 把这两件事分开，是为了让"换个接口"永远不需要动传输层，
 * 而"改传输策略（重试、鉴权、脱敏）"也不必扫一遍所有接口定义。
 *
 * <p><b>三件事在这里被一次性收口，避免在每个调用点重复：</b>
 * <ol>
 *   <li>解开统一响应包装 —— 调用方拿到的直接是 `data`，不必人人写一遍 `res.code === 0 ? res.data : ...`；</li>
 *   <li>把 `code !== 0` 变成异常 —— 用返回值表达失败，必然有人漏判，而漏判的表现是"页面显示 undefined"；</li>
 *   <li>超时与取消合并成一个信号 —— 两套中断机制并存时，总有一边忘了清定时器。</li>
 * </ol>
 */
export function createHttpClient(config: HttpConfig = {}): HttpClient {
  const baseUrl = config.baseUrl ?? '';
  const defaultTimeout = config.timeout ?? DEFAULT_TIMEOUT;
  const successCode = config.successCode ?? DEFAULT_SUCCESS_CODE;
  const defaultHeaders = config.headers ?? {};
  const defaultUnwrap = config.unwrap ?? true;
  const traceIdProvider = config.traceIdProvider;
  const fetchImpl = config.fetchImpl;
  /*
   * 一次性确定实际使用的 fetch，而不是在请求路径上做三元判断：
   *
   * 1. 判断放在这里，`fetchImpl` 的可空性不必跨越 await 保持收窄；
   * 2. 浏览器里 `fetch` 以无接收者方式调用会抛 "Illegal invocation"，
   *    所以必须写成成员调用（`globalThis.fetch(...)`）而不是取出函数引用再调。
   */
  const doFetch: typeof fetch = fetchImpl
    ? fetchImpl
    : (input, init) => globalThis.fetch(input, init);
  const log: Logger | null = config.logger === undefined ? rootLogger.child('http') : config.logger;
  const accessTokenProvider = config.accessTokenProvider;
  const onUnauthorized = config.onUnauthorized;
  const onAuthLost = config.onAuthLost;

  /**
   * 认证恢复去重（single-flight）。
   *
   * <p><b>这不是性能优化，是正确性要求。</b>刷新令牌是**轮换**的：并发的第二次刷新
   * 持的是刚被作废的旧令牌，服务端会判成重放攻击并撤销该用户**全部**会话。
   * 因此同一个客户端上的并发 401 必须合并成一次恢复 —— 见 `docs/decisions/0018`。
   *
   * <p>处理器抛错按"恢复失败"处理，不上抛：它只是失败链条上的一环，
   * 调用方真正关心的是"我这次请求失败了"，而不是"刷新接口报错了"。
   */
  let pendingReauth: Promise<boolean> | null = null;

  function reauthenticate(context: UnauthorizedContext): Promise<boolean> {
    const handler = onUnauthorized;
    if (!handler) return Promise.resolve(false);
    if (!pendingReauth) {
      const started = Promise.resolve()
        .then(() => handler(context))
        .then(
          (recovered) => recovered === true,
          (cause: unknown) => {
            log?.warn('认证恢复失败，按未恢复处理', {
              message: cause instanceof Error ? cause.message : String(cause),
            });
            return false;
          },
        );
      pendingReauth = started.finally(() => {
        pendingReauth = null;
      });
    }
    return pendingReauth;
  }

  function reportError(error: HttpError, silent: boolean | undefined): void {
    config.onError?.(error);
    if (silent || !log) return;

    // 业务失败记 warn：它是预期的拒绝，不该和真正的故障进同一个告警通道。
    const write = error.kind === 'business' ? log.warn.bind(log) : log.error.bind(log);
    write(`${error.method} ${error.url} 失败｜${error.kind}｜${error.message}`, {
      status: error.status,
      code: error.code,
      traceId: error.traceId,
    });
  }

  async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    const method: HttpMethod = options.method ?? 'GET';
    const url = joinUrl(baseUrl, path) + buildQuery(options.query);
    const timeout = options.timeout ?? defaultTimeout;
    const shouldUnwrap = options.unwrap ?? defaultUnwrap;

    const controller = new AbortController();
    const externalSignal = options.signal;
    if (externalSignal) {
      if (externalSignal.aborted) controller.abort(externalSignal.reason);
      else {
        externalSignal.addEventListener('abort', () => controller.abort(externalSignal.reason), {
          once: true,
        });
      }
    }

    let timedOut = false;
    const timer =
      timeout > 0
        ? setTimeout(() => {
            timedOut = true;
            controller.abort();
          }, timeout)
        : undefined;

    const headers: Record<string, string> = {
      Accept: 'application/json',
      ...defaultHeaders,
      ...options.headers,
    };

    if (traceIdProvider) {
      const traceId = traceIdProvider();
      if (traceId && !findHeaderKey(headers, 'x-trace-id')) headers['X-Trace-Id'] = traceId;
    }

    let body: string | undefined;
    if (options.body !== undefined) {
      if (typeof options.body === 'string') {
        body = options.body;
      } else {
        body = JSON.stringify(options.body);
        if (!findHeaderKey(headers, 'content-type')) headers['Content-Type'] = 'application/json';
      }
    }

    const context: RequestContext = { url, method, headers, body };
    // 只在真的有钩子时才 await：无条件 await 会白白让出一次微任务，
    // 于是 fetch 永远晚一拍才被调用 —— 调用方在拿到 Promise 后立刻 abort 时，
    // 请求还没发出去就已经被标记为中断，行为与"取消一个进行中的请求"完全不同。
    if (config.onRequest) await config.onRequest(context);

    /** 内核注入令牌时用的实际头名（调用方可能写成小写的 `authorization`，需按原名改写） */
    let injectedHeaderKey: string | undefined;

    /** 是否"带着令牌"发出 —— 没带令牌却收到 401 时，刷新没有意义，不该白跑一次 */
    let sentWithToken = false;

    /**
     * 取一次令牌并注入。
     *
     * <p>**重试路径必须重新调用**：刷新成功后令牌已经换了，
     * 沿用第一次取到的值等于带着刚作废的令牌再撞一次 401。
     */
    function attachToken(): void {
      if (!options.skipAuth && accessTokenProvider) {
        const token = accessTokenProvider();
        if (token) {
          const existing = findHeaderKey(headers, 'authorization');
          // 调用方显式设置的头**不覆盖**；但内核上一轮注入的**必须覆盖**
          if (!existing) {
            headers.Authorization = `Bearer ${token}`;
            injectedHeaderKey = 'Authorization';
          } else if (existing === injectedHeaderKey) {
            headers[existing] = `Bearer ${token}`;
          }
        }
      }
      sentWithToken = findHeaderKey(headers, 'authorization') !== undefined;
    }

    attachToken();

    try {
      /*
       * 最多两次：首次 + 认证恢复后的一次重试。
       *
       * 刻意不做得更多：401 反复出现说明问题不在"令牌过期"，而在配置或权限侧，
       * 重试只会把问题放大成一串请求。
       */
      for (let attempt = 1; ; attempt += 1) {
        let response: Response;
        try {
          response = await doFetch(url, { method, headers, body, signal: controller.signal });
        } catch (cause) {
          const transportError = toTransportError(cause, { url, method, timedOut, timeout });
          reportError(transportError, options.silent);
          throw transportError;
        }

        config.onResponse?.({ ...context, status: response.status, response });

        const traceId = response.headers.get('x-trace-id') ?? undefined;

        // 204 无内容：按约定直接返回 undefined，不当作"响应体不符合结构"
        if (response.status === 204) return undefined as T;

        const payload = await readBody(response);

        if (!response.ok) {
          const error = new HttpStatusError({
            message:
              extractMessage(payload) ?? `HTTP ${response.status} ${response.statusText}`.trim(),
            url,
            method,
            status: response.status,
            traceId,
            data: payload,
          });

          /*
           * 只有 401 值得救：403 是"登录了但没这个权限"，换多少个令牌也换不来权限。
           */
          const recoverable =
            response.status === 401 &&
            attempt === 1 &&
            sentWithToken &&
            options.skipAuthRetry !== true &&
            onUnauthorized !== undefined &&
            !controller.signal.aborted;

          if (recoverable) {
            const recovered = await reauthenticate({ url, method });
            if (recovered && !controller.signal.aborted) {
              log?.debug('收到 401，会话已恢复，重试一次', { url, method });
              attachToken();
              continue;
            }
          }

          // 带着令牌仍 401，而恢复已无意义（或恢复后重试依然 401）⇒ 会话确实失效了
          if (response.status === 401 && sentWithToken && options.skipAuthRetry !== true) {
            onAuthLost?.();
          }

          reportError(error, options.silent);
          throw error;
        }

        if (!shouldUnwrap) return payload as T;

        if (!isEnvelope(payload)) {
          const error = new ParseError({
            message: '响应体不符合统一响应结构（缺少 code / data 字段）',
            url,
            method,
            status: response.status,
            traceId,
            data: payload,
          });
          reportError(error, options.silent);
          throw error;
        }

        if (payload.code !== successCode) {
          const error = new BusinessError({
            message: payload.message || `业务失败（code=${payload.code}）`,
            url,
            method,
            status: response.status,
            code: payload.code,
            traceId,
            data: payload.data,
          });
          reportError(error, options.silent);
          throw error;
        }

        return payload.data as T;
      }
    } finally {
      if (timer) clearTimeout(timer);
    }
  }

  return {
    baseUrl,
    request,
    get: (path, options) => request(path, { ...options, method: 'GET' }),
    post: (path, body, options) => request(path, { ...options, method: 'POST', body }),
    put: (path, body, options) => request(path, { ...options, method: 'PUT', body }),
    patch: (path, body, options) => request(path, { ...options, method: 'PATCH', body }),
    delete: (path, options) => request(path, { ...options, method: 'DELETE' }),
  };
}

interface TransportErrorContext {
  url: string;
  method: HttpMethod;
  timedOut: boolean;
  timeout: number;
}

/**
 * 把 fetch 抛出的原始错误翻译成有语义的分类。
 *
 * <p>顺序很重要：**先判断"是不是我们主动超时中断的"**，再看错误名。
 * 因为超时也是通过 `abort()` 实现的，两者抛出的都是 `AbortError`；
 * 若先按 `AbortError` 归类，超时就会被报成"被主动取消"，
 * 排查时会去找"谁取消了这个请求"，而真正的原因（服务端太慢）被完全掩盖。
 */
function toTransportError(cause: unknown, ctx: TransportErrorContext): HttpError {
  if (ctx.timedOut) {
    return new TimeoutError({
      message: `请求超时（${ctx.timeout}ms）`,
      url: ctx.url,
      method: ctx.method,
      cause,
    });
  }

  if (cause instanceof DOMException && cause.name === 'AbortError') {
    return new AbortError({ message: '请求已被取消', url: ctx.url, method: ctx.method, cause });
  }

  const detail = cause instanceof Error ? cause.message : String(cause);
  return new NetworkError({
    message: `网络请求失败：${detail}`,
    url: ctx.url,
    method: ctx.method,
    cause,
  });
}
