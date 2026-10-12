import type { Logger } from '../logger';
import type { HttpError } from './errors';

/** 支持的请求方法。刻意不含 OPTIONS/HEAD：前者由浏览器自动处理，后者本项目用不到 */
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

/** 查询参数取值。`null` / `undefined` / 空串一律**不发送**，而不是发成 `key=null` */
export type QueryValue = string | number | boolean | null | undefined;

/**
 * 服务端统一响应包装，与后端 `sts-common-basic` 的 `R<T>` 一一对应。
 *
 * <p>`code === 0` 表示业务成功；HTTP 状态码只表达传输层结果，
 * 业务失败同样是 HTTP 200。
 */
export interface ApiEnvelope<T> {
  code: number;
  message: string;
  data: T;
  timestamp?: number;
}

/** 请求发出前的上下文，可在 `onRequest` 里改写（如补签名头） */
export interface RequestContext {
  url: string;
  method: HttpMethod;
  headers: Record<string, string>;
  body?: string;
}

/** 响应上下文 */
export interface ResponseContext extends RequestContext {
  status: number;
  response: Response;
}

/** 认证失效（401）时的上下文 */
export interface UnauthorizedContext {
  url: string;
  method: HttpMethod;
}

/**
 * 认证失效处理器。
 *
 * <p>返回 `true` 表示"令牌已换新，可以重试原请求"；返回 `false` 表示"恢复不了，按失败处理"。
 *
 * <p><b>为什么做成钩子而不是把刷新写进内核：</b>内核不认识"刷新令牌接口"（那是业务契约），
 * 也不该认识登录页（那是界面）。它只负责按顺序问一句"还能救吗"，怎么救由应用层决定。
 * 这与 `traceIdProvider` 同一套路：内核只留注入位。
 *
 * <p>内核保证**同一个客户端上的并发 401 只会调用它一次**（single-flight），
 * 因为刷新令牌是轮换的 —— 并发的第二个刷新请求持的是刚被作废的旧令牌，
 * 服务端会把它判成重放攻击并撤销该用户的全部会话（见 `docs/decisions/0018`）。
 */
export type UnauthorizedHandler = (context: UnauthorizedContext) => boolean | Promise<boolean>;

/**
 * 会话彻底失效时的回调。
 *
 * <p>在"带着令牌的请求拿到 401，且已尝试过恢复（或恢复后重试仍然 401）"时调用一次。
 * 应用层在这里清会话、跳登录页 —— 内核不做这件事，它不碰界面也不碰存储。
 */
export type AuthLostHandler = () => void;

/** 单次请求选项，覆盖客户端级配置 */
export interface RequestOptions {
  method?: HttpMethod;
  /** 查询参数，自动拼接与编码 */
  query?: Record<string, QueryValue>;
  /** 请求体。对象会被 JSON 序列化并自动带上 Content-Type */
  body?: unknown;
  headers?: Record<string, string>;
  /** 外部取消信号，会与超时信号合并 */
  signal?: AbortSignal;
  /** 本次请求的超时毫秒数 */
  timeout?: number;
  /** 本次是否解开统一响应包装 */
  unwrap?: boolean;
  /** 静默：不写日志。用于已知会失败、由调用方自行处理的探测类请求 */
  silent?: boolean;
  /** 本次不注入访问令牌（公开接口用） */
  skipAuth?: boolean;
  /**
   * 本次不参与 401 自动恢复与重试。
   *
   * <p>**刷新令牌的请求本身必须置位**：否则刷新失败 → 401 → 触发刷新 → 再失败…
   * 递归下去，最终表现为刷新接口被自己打出一片 401。
   */
  skipAuthRetry?: boolean;
}

/** 客户端级配置 */
export interface HttpConfig {
  /** 基地址，如 `/api` 或 `https://api.example.com` */
  baseUrl?: string;
  /** 超时毫秒数，0 表示不超时 */
  timeout?: number;
  /** 视为业务成功的 code，默认 0 */
  successCode?: number;
  /** 默认请求头 */
  headers?: Record<string, string>;
  /** 是否默认解开统一响应包装，默认 true */
  unwrap?: boolean;
  /**
   * 链路标识提供者。返回非空值时自动注入 `X-Trace-Id` 请求头，
   * 让前端日志与后端日志能通过同一个 id 串联（若不注入，排查一次失败请求
   * 就得在两个系统里按时间戳手工对齐）。
   */
  traceIdProvider?: () => string | undefined;
  /**
   * 访问令牌提供者。返回非空值时自动注入 `Authorization: Bearer <token>`。
   *
   * <p>**每次重试都会重新调用它**（而不是沿用第一次取到的值）—— 否则刷新之后
   * 重试仍然带着那个已经作废的令牌，白跑一次。
   */
  accessTokenProvider?: () => string | undefined | null;
  /** 认证失效处理器（401）。见 {@link UnauthorizedHandler} */
  onUnauthorized?: UnauthorizedHandler;
  /** 会话彻底失效回调。见 {@link AuthLostHandler} */
  onAuthLost?: AuthLostHandler;
  /** 更换 fetch 实现（测试注入 / 兼容旧环境） */
  fetchImpl?: typeof fetch;
  /** 日志器；传 `null` 关闭日志 */
  logger?: Logger | null;
  onRequest?: (context: RequestContext) => void | Promise<void>;
  onResponse?: (context: ResponseContext) => void;
  onError?: (error: HttpError) => void;
}

/** 请求客户端 */
export interface HttpClient {
  readonly baseUrl: string;
  request<T>(path: string, options?: RequestOptions): Promise<T>;
  get<T>(path: string, options?: Omit<RequestOptions, 'method' | 'body'>): Promise<T>;
  post<T>(
    path: string,
    body?: unknown,
    options?: Omit<RequestOptions, 'method' | 'body'>,
  ): Promise<T>;
  put<T>(
    path: string,
    body?: unknown,
    options?: Omit<RequestOptions, 'method' | 'body'>,
  ): Promise<T>;
  patch<T>(
    path: string,
    body?: unknown,
    options?: Omit<RequestOptions, 'method' | 'body'>,
  ): Promise<T>;
  delete<T>(path: string, options?: Omit<RequestOptions, 'method' | 'body'>): Promise<T>;
}
