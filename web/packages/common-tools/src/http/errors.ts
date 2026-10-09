import type { HttpMethod } from './types';

/**
 * 错误分类。
 *
 * <p><b>为什么一定要分类，而不是一个 `Error` 打天下：</b>调用方对不同的失败需要完全不同的反应 ——
 * 网络抖动值得重试，业务拒绝重试一万次也是同样结果，超时则要先搞清楚是服务慢还是网络差。
 * 只给一句 `Error: request failed`，调用方只能靠匹配错误文案来猜，
 * 而文案是随时会被改掉的。
 */
export type HttpErrorKind =
  /** 业务失败（HTTP 200 但 code !== 0）：请求本身完全正常，是业务规则拒绝了它 */
  | 'business'
  /** HTTP 状态码非 2xx：传输层或网关层面的失败 */
  | 'http'
  /** 请求没能发出去或没收到响应（DNS、连接被拒、CORS 被拦） */
  | 'network'
  /** 超时 */
  | 'timeout'
  /** 被主动取消 */
  | 'abort'
  /** 响应无法按约定解析（不是合法 JSON，或不符合统一响应结构） */
  | 'parse';

export interface HttpErrorInit {
  message: string;
  url: string;
  method: HttpMethod;
  status?: number;
  code?: number;
  traceId?: string;
  /** 服务端返回的 data，业务失败时可能带有补充信息 */
  data?: unknown;
  cause?: unknown;
}

/**
 * 请求相关错误的基类。
 *
 * <p>保留 `kind` / `status` / `code` / `traceId` 四个结构化字段，让调用方能**按结构判断**，
 * 而不是按错误文案做字符串匹配 —— 后者在文案改一个字之后就会静默失效。
 */
export class HttpError extends Error {
  readonly kind: HttpErrorKind;

  readonly url: string;

  readonly method: HttpMethod;

  /** HTTP 状态码，仅 `http` 类错误有值 */
  readonly status: number | undefined;

  /** 业务错误码，仅 `business` 类错误有值 */
  readonly code: number | undefined;

  /** 链路标识，用于到日志系统里追这一次请求 */
  readonly traceId: string | undefined;

  readonly data: unknown;

  constructor(kind: HttpErrorKind, init: HttpErrorInit) {
    super(init.message, init.cause === undefined ? undefined : { cause: init.cause });
    this.name = 'HttpError';
    this.kind = kind;
    this.url = init.url;
    this.method = init.method;
    this.status = init.status;
    this.code = init.code;
    this.traceId = init.traceId;
    this.data = init.data;
  }

  /**
   * 是否值得重试。
   *
   * <p>判据只有一条：**同样的请求再发一次，结果是否可能不同**。
   * 网络错误、超时、5xx、429（限流）符合；业务拒绝（参数错、无权限、余额不足）
   * 不符合 —— 重试它们只会把一次错误放大成 N 次。
   */
  get retryable(): boolean {
    switch (this.kind) {
      case 'network':
      case 'timeout':
        return true;
      case 'http':
        return this.status === 429 || (this.status !== undefined && this.status >= 500);
      default:
        return false;
    }
  }
}

/** 业务失败：HTTP 200 但 `code !== 0` */
export class BusinessError extends HttpError {
  constructor(init: HttpErrorInit) {
    super('business', init);
    this.name = 'BusinessError';
  }
}

/** HTTP 状态码非 2xx */
export class HttpStatusError extends HttpError {
  constructor(init: HttpErrorInit) {
    super('http', init);
    this.name = 'HttpStatusError';
  }
}

/** 请求未能送达 */
export class NetworkError extends HttpError {
  constructor(init: HttpErrorInit) {
    super('network', init);
    this.name = 'NetworkError';
  }
}

/** 超时 */
export class TimeoutError extends HttpError {
  constructor(init: HttpErrorInit) {
    super('timeout', init);
    this.name = 'TimeoutError';
  }
}

/** 被主动取消 */
export class AbortError extends HttpError {
  constructor(init: HttpErrorInit) {
    super('abort', init);
    this.name = 'AbortError';
  }
}

/** 响应无法按约定解析 */
export class ParseError extends HttpError {
  constructor(init: HttpErrorInit) {
    super('parse', init);
    this.name = 'ParseError';
  }
}

/** 类型守卫：判断任意值是否为请求错误 */
export function isHttpError(value: unknown): value is HttpError {
  return value instanceof HttpError;
}
