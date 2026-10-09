import { describe, expect, it, vi } from 'vitest';

import {
  AbortError,
  BusinessError,
  createHttpClient,
  HttpStatusError,
  NetworkError,
  ParseError,
  TimeoutError,
  isHttpError,
} from '../src/http';

/** 最小可用的 Response 替身：只实现客户端真正会用到的那几个成员 */
function fakeResponse(
  init: {
    status?: number;
    statusText?: string;
    body?: string;
    headers?: Record<string, string>;
  } = {},
): Response {
  const status = init.status ?? 200;
  const headers = new Map(
    Object.entries(init.headers ?? {}).map(([key, value]) => [key.toLowerCase(), value]),
  );
  return {
    status,
    statusText: init.statusText ?? '',
    ok: status >= 200 && status < 300,
    headers: { get: (name: string) => headers.get(name.toLowerCase()) ?? null },
    text: async () => init.body ?? '',
  } as unknown as Response;
}

function envelope(code: number, data: unknown, message = 'ok'): string {
  return JSON.stringify({ code, message, data, timestamp: 1 });
}

/** 构造一个读取全部参数的 fetch 替身 */
function okFetch(body: string, status = 200) {
  return vi.fn(async () => fakeResponse({ status, body })) as unknown as typeof fetch;
}

/** 永不 resolve、只在收到 abort 时 reject 的 fetch 替身 —— 用于验证超时与取消 */
function hangingFetch() {
  return vi.fn(
    (_url: string, init?: RequestInit) =>
      new Promise<Response>((_resolve, reject) => {
        const abort = () => reject(new DOMException('Aborted', 'AbortError'));
        // 与真实 fetch 语义对齐：传入已中断的信号会立即失败，
        // 而不是静静等待一个永远不会再触发的 abort 事件
        if (init?.signal?.aborted) {
          abort();
          return;
        }
        init?.signal?.addEventListener('abort', abort);
      }),
  ) as unknown as typeof fetch;
}

const silent = { logger: null } as const;

describe('createHttpClient · 解包与错误分类', () => {
  it('解开统一响应包装，直接返回 data', async () => {
    const client = createHttpClient({
      ...silent,
      fetchImpl: okFetch(envelope(0, { status: 'UP' })),
    });

    await expect(client.get('/api/health')).resolves.toEqual({ status: 'UP' });
  });

  it('code !== 0 抛出 BusinessError，并携带业务码与 data', async () => {
    const client = createHttpClient({
      ...silent,
      fetchImpl: okFetch(envelope(40400, null, '资源不存在')),
    });

    const error = await client.get('/api/x').catch((e: unknown) => e);

    expect(error).toBeInstanceOf(BusinessError);
    expect(isHttpError(error)).toBe(true);
    const business = error as BusinessError;
    expect(business.code).toBe(40400);
    expect(business.message).toBe('资源不存在');
    // 业务失败不是 HTTP 失败，状态码仍是 200
    expect(business.status).toBe(200);
  });

  it('HTTP 500 抛出 HttpStatusError', async () => {
    const client = createHttpClient({
      ...silent,
      fetchImpl: okFetch(JSON.stringify({ message: '网关炸了' }), 500),
    });

    const error = (await client.get('/api/x').catch((e: unknown) => e)) as HttpStatusError;

    expect(error).toBeInstanceOf(HttpStatusError);
    expect(error.status).toBe(500);
    expect(error.message).toBe('网关炸了');
  });

  it('响应体不符合统一结构时抛 ParseError，而不是悄悄返回 undefined', async () => {
    const client = createHttpClient({ ...silent, fetchImpl: okFetch('<html>502</html>') });

    await expect(client.get('/api/x')).rejects.toBeInstanceOf(ParseError);
  });

  it('unwrap: false 时返回原始信封', async () => {
    const client = createHttpClient({
      ...silent,
      unwrap: false,
      fetchImpl: okFetch(envelope(0, [1, 2])),
    });

    const result = await client.get<{ code: number; data: number[] }>('/api/x');

    expect(result.code).toBe(0);
    expect(result.data).toEqual([1, 2]);
  });
});

describe('createHttpClient · 可重试性判据', () => {
  it('网络错误、超时、5xx、429 可重试；业务拒绝不可重试', () => {
    const network = new NetworkError({ message: '', url: '', method: 'GET' });
    const timeout = new TimeoutError({ message: '', url: '', method: 'GET' });
    const serverError = new HttpStatusError({ message: '', url: '', method: 'GET', status: 500 });
    const rateLimited = new HttpStatusError({ message: '', url: '', method: 'GET', status: 429 });
    const badRequest = new HttpStatusError({ message: '', url: '', method: 'GET', status: 400 });
    const business = new BusinessError({ message: '', url: '', method: 'GET', code: 40400 });

    expect(network.retryable).toBe(true);
    expect(timeout.retryable).toBe(true);
    expect(serverError.retryable).toBe(true);
    expect(rateLimited.retryable).toBe(true);
    expect(badRequest.retryable).toBe(false);
    // 重试业务拒绝只会把一次错误放大成 N 次
    expect(business.retryable).toBe(false);
  });
});

describe('createHttpClient · URL 与请求头', () => {
  it('拼接基地址与查询参数，跳过空值', async () => {
    const fetchImpl = okFetch(envelope(0, null));
    const client = createHttpClient({ ...silent, baseUrl: '/api/', fetchImpl });

    await client.get('/logs', {
      query: { page: 1, size: 20, level: '', app: undefined, keyword: null, flag: false },
    });

    const url = (fetchImpl as unknown as { mock: { calls: [string][] } }).mock.calls[0][0];
    expect(url).toBe('/api/logs?page=1&size=20&flag=false');
  });

  it('对象请求体自动序列化并补上 Content-Type', async () => {
    const fetchImpl = okFetch(envelope(0, null));
    const client = createHttpClient({ ...silent, fetchImpl });

    await client.post('/logs', { app: 'demo', logs: [] });

    const init = (fetchImpl as unknown as { mock: { calls: [string, RequestInit][] } }).mock
      .calls[0][1];
    expect(init.body).toBe('{"app":"demo","logs":[]}');
    expect(init.headers).toMatchObject({ 'Content-Type': 'application/json' });
  });

  it('注入 traceId，但不覆盖调用方已显式设置的同名头', async () => {
    const fetchImpl = okFetch(envelope(0, null));
    const client = createHttpClient({
      ...silent,
      fetchImpl,
      traceIdProvider: () => 'trace-from-provider',
    });

    await client.get('/a');
    await client.get('/b', { headers: { 'x-trace-id': 'trace-explicit' } });

    const calls = (fetchImpl as unknown as { mock: { calls: [string, RequestInit][] } }).mock.calls;
    expect(calls[0][1].headers).toMatchObject({ 'X-Trace-Id': 'trace-from-provider' });
    expect(calls[1][1].headers).toMatchObject({ 'x-trace-id': 'trace-explicit' });
    // 大小写不敏感地判重，避免同时出现两个语义相同的头
    const keys = Object.keys(calls[1][1].headers as Record<string, string>);
    expect(keys.filter((key) => key.toLowerCase() === 'x-trace-id')).toHaveLength(1);
  });

  it('回传响应中的 traceId，便于到日志系统追这一次请求', async () => {
    const withTrace = createHttpClient({
      ...silent,
      fetchImpl: (async () =>
        fakeResponse({
          status: 500,
          body: JSON.stringify({ message: 'boom' }),
          headers: { 'x-trace-id': 'trace-abc' },
        })) as unknown as typeof fetch,
    });

    const error = (await withTrace.get('/x').catch((e: unknown) => e)) as HttpStatusError;

    expect(error.traceId).toBe('trace-abc');
  });
});

describe('createHttpClient · 超时与取消', () => {
  it('超时抛 TimeoutError，而不是被误报成主动取消', async () => {
    const client = createHttpClient({ ...silent, timeout: 20, fetchImpl: hangingFetch() });

    const error = await client.get('/slow').catch((e: unknown) => e);

    // 若分类顺序写反（先按 AbortError 判断），这里会变成 AbortError，
    // 排查方向就会被带向"谁取消了这个请求"，而真实原因是服务端太慢
    expect(error).toBeInstanceOf(TimeoutError);
    expect((error as TimeoutError).retryable).toBe(true);
  });

  it('外部 signal 取消时抛 AbortError', async () => {
    const controller = new AbortController();
    const client = createHttpClient({ ...silent, fetchImpl: hangingFetch() });

    const pending = client.get('/x', { signal: controller.signal }).catch((e: unknown) => e);
    controller.abort();

    await expect(pending).resolves.toBeInstanceOf(AbortError);
  });

  it('已取消的 signal 传入时立即可用，不产生悬挂请求', async () => {
    const controller = new AbortController();
    controller.abort();
    const client = createHttpClient({ ...silent, fetchImpl: hangingFetch() });

    await expect(client.get('/x', { signal: controller.signal })).rejects.toBeInstanceOf(
      AbortError,
    );
  });
});
