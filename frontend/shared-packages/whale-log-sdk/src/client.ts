/**
 * 上报客户端：基于 fetch 的默认传输实现，装配 {@code X-Trace-Id} 请求头。
 *
 * <p>回环防护：上报使用 fetch 而非 XHR / history API，不会触发本 SDK 的行为埋点；
 * 上报失败返回 false 交由队列重试，异常不冒泡。</p>
 */

import { HEADER_TRACE_ID } from './constants';
import type { LogBatchRequest, Transport } from './types';

/**
 * 创建默认 fetch 传输。
 *
 * @returns 传输函数：投递成功返回 true，网络异常返回 false
 */
export function createFetchTransport(): Transport {
  return async (url: string, body: LogBatchRequest): Promise<boolean> => {
    try {
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          [HEADER_TRACE_ID]: body.traceId ?? '',
        },
        body: JSON.stringify(body),
        keepalive: true,
      });
      return response.ok;
    } catch {
      return false;
    }
  };
}
