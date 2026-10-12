import { createHttpClient } from 'common-tools/http';
import type { HttpClient, HttpConfig } from 'common-tools/http';

import { createAuthApi } from './auth';
import type { AuthApi } from './auth';
import { createEventApi } from './events';
import type { EventApi } from './events';
import { createLogApi } from './log';
import type { LogApi } from './log';
import { createSystemApi } from './system';
import type { SystemApi } from './system';

/** 服务端接口的聚合入口 */
export interface ApiClient {
  /** 底层请求客户端，需要临时调一个尚未纳入契约的接口时用 */
  readonly http: HttpClient;
  readonly auth: AuthApi;
  readonly log: LogApi;
  readonly event: EventApi;
  readonly system: SystemApi;
}

/**
 * 创建接口客户端。
 *
 * <p><b>这一层只做装配，不做别的事。</b>请求怎么发、错误怎么翻译，全在
 * `common-tools/http` 的请求内核里；接口长什么样，全在契约生成的类型里。
 * 本层把两者接起来，因此"换一个接口"永远不用碰传输层，
 * "改传输策略（鉴权、重试、脱敏）"也不用扫一遍接口定义。
 *
 * <p>典型用法：
 * ```ts
 * const api = createApiClient({
 *   baseUrl: '/api',
 *   traceIdProvider: () => currentTraceId,
 * });
 * const page = await api.log.page({ level: 'error', page: 1 });
 * ```
 */
export function createApiClient(config: HttpConfig = {}): ApiClient {
  const http = createHttpClient(config);
  return {
    http,
    auth: createAuthApi(http),
    log: createLogApi(http),
    event: createEventApi(http),
    system: createSystemApi(http),
  };
}
