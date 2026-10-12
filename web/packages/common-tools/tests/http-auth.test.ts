import { describe, expect, it, vi } from 'vitest';

import { createHttpClient, HttpStatusError } from '../src/http';
import type { HttpMethod } from '../src/http';

/** 最小可用的 Response 替身 */
function fakeResponse(init: { status?: number; body?: string } = {}): Response {
  const status = init.status ?? 200;
  return {
    status,
    statusText: '',
    ok: status >= 200 && status < 300,
    headers: { get: () => null },
    text: async () => init.body ?? '',
  } as unknown as Response;
}

function envelope(data: unknown, code = 0): string {
  return JSON.stringify({ code, message: 'ok', data, timestamp: 1 });
}

interface Recorded {
  url: string;
  method: string;
  authorization?: string;
}

/**
 * 按调用顺序返回预设响应的 fetch 替身，并记录每次请求的头。
 *
 * <p>顺序响应比"按 URL 路由"更适合本组用例：这里要验证的正是
 * **同一个 URL 被打了第二次**（401 之后带上新令牌重试）。
 */
function sequencedFetch(responses: Array<{ status: number; body?: string }>) {
  const calls: Recorded[] = [];
  let index = 0;
  const impl = vi.fn(async (url: string, init?: RequestInit) => {
    const headers = (init?.headers ?? {}) as Record<string, string>;
    const key = Object.keys(headers).find((name) => name.toLowerCase() === 'authorization');
    calls.push({
      url,
      method: (init?.method as string) ?? 'GET',
      authorization: key ? headers[key] : undefined,
    });
    const next = responses[Math.min(index, responses.length - 1)];
    index += 1;
    return fakeResponse(next);
  }) as unknown as typeof fetch;
  return { impl, calls };
}

/** 401 的响应体：后端在未认证时也走统一响应结构 */
const UNAUTHORIZED = { status: 401, body: envelope(null, 40101) };

const silent = { logger: null } as const;

describe('createHttpClient · 令牌注入', () => {
  it('从 accessTokenProvider 取值并注入 Authorization 头', async () => {
    const { impl, calls } = sequencedFetch([{ status: 200, body: envelope({ ok: 1 }) }]);
    const client = createHttpClient({
      ...silent,
      accessTokenProvider: () => 'token-a',
      fetchImpl: impl,
    });

    await client.get('/api/logs/client');

    expect(calls[0].authorization).toBe('Bearer token-a');
  });

  it('skipAuth 的请求不带令牌（公开接口用）', async () => {
    const { impl, calls } = sequencedFetch([{ status: 200, body: envelope({ ok: 1 }) }]);
    const client = createHttpClient({
      ...silent,
      accessTokenProvider: () => 'token-a',
      fetchImpl: impl,
    });

    await client.post('/api/auth/login', { username: 'admin' }, { skipAuth: true });

    expect(calls[0].authorization).toBeUndefined();
  });

  it('不覆盖调用方显式设置的 Authorization 头', async () => {
    const { impl, calls } = sequencedFetch([{ status: 200, body: envelope({ ok: 1 }) }]);
    const client = createHttpClient({
      ...silent,
      accessTokenProvider: () => 'token-a',
      fetchImpl: impl,
    });

    await client.get('/api/logs/client', { headers: { Authorization: 'Bearer manual' } });

    expect(calls[0].authorization).toBe('Bearer manual');
  });
});

describe('createHttpClient · 401 恢复', () => {
  it('401 时调用恢复钩子，带着**新**令牌重试一次', async () => {
    const { impl, calls } = sequencedFetch([
      UNAUTHORIZED,
      { status: 200, body: envelope({ recovered: true }) },
    ]);
    let token = 'old';
    const onUnauthorized = vi.fn(async () => {
      token = 'new';
      return true;
    });
    const client = createHttpClient({
      ...silent,
      accessTokenProvider: () => token,
      onUnauthorized,
      fetchImpl: impl,
    });

    const data = await client.get<{ recovered: boolean }>('/api/logs/client');

    expect(data.recovered).toBe(true);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
    expect(calls).toHaveLength(2);
    // 关键：重试必须带新令牌，沿用旧令牌等于再撞一次 401
    expect(calls[0].authorization).toBe('Bearer old');
    expect(calls[1].authorization).toBe('Bearer new');
  });

  it('403 不触发恢复：换多少令牌也换不来权限', async () => {
    const { impl } = sequencedFetch([{ status: 403, body: envelope(null, 40301) }]);
    const onUnauthorized = vi.fn(async () => true);
    const client = createHttpClient({
      ...silent,
      accessTokenProvider: () => 'token-a',
      onUnauthorized,
      fetchImpl: impl,
    });

    await expect(client.get('/api/auth/users')).rejects.toMatchObject({ status: 403 });
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('skipAuthRetry 的请求不参与恢复（刷新接口自身如此）', async () => {
    const { impl, calls } = sequencedFetch([UNAUTHORIZED]);
    const onUnauthorized = vi.fn(async () => true);
    const client = createHttpClient({
      ...silent,
      accessTokenProvider: () => 'token-a',
      onUnauthorized,
      fetchImpl: impl,
    });

    await expect(client.post('/api/auth/refresh', {}, { skipAuthRetry: true })).rejects.toThrow();
    expect(onUnauthorized).not.toHaveBeenCalled();
    expect(calls).toHaveLength(1);
  });

  it('没带令牌却收到 401 时不触发恢复（白跑一次没有意义）', async () => {
    const { impl } = sequencedFetch([UNAUTHORIZED]);
    const onUnauthorized = vi.fn(async () => true);
    const client = createHttpClient({
      ...silent,
      accessTokenProvider: () => null,
      onUnauthorized,
      fetchImpl: impl,
    });

    await expect(client.get('/api/logs/client')).rejects.toThrow();
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('并发 401 只触发一次恢复，且两个请求都用新令牌重试', async () => {
    const { impl, calls } = sequencedFetch([
      UNAUTHORIZED,
      UNAUTHORIZED,
      { status: 200, body: envelope({ ok: 1 }) },
      { status: 200, body: envelope({ ok: 2 }) },
    ]);
    let token = 'old';
    let resolveRefresh: () => void = () => {};
    const onUnauthorized = vi.fn(
      () =>
        new Promise<boolean>((resolve) => {
          resolveRefresh = () => {
            token = 'new';
            resolve(true);
          };
        }),
    );
    const client = createHttpClient({
      ...silent,
      accessTokenProvider: () => token,
      onUnauthorized,
      fetchImpl: impl,
    });

    const first = client.get('/api/logs/client');
    const second = client.get('/api/auth/users');
    // 等两个请求都撞上 401、都进入"等待恢复"状态之后再放行刷新
    await vi.waitFor(() => expect(onUnauthorized).toHaveBeenCalled());
    resolveRefresh();
    const results = await Promise.all([first, second]);

    expect(results).toEqual([{ ok: 1 }, { ok: 2 }]);
    // single-flight：并发的第二次刷新会被服务端判成重放攻击，必须合并
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
    expect(calls.filter((call) => call.authorization === 'Bearer new')).toHaveLength(2);
  });

  it('恢复失败时抛原始 401，并通知会话已失效', async () => {
    const { impl } = sequencedFetch([UNAUTHORIZED]);
    const onAuthLost = vi.fn();
    const client = createHttpClient({
      ...silent,
      accessTokenProvider: () => 'token-a',
      onUnauthorized: async () => false,
      onAuthLost,
      fetchImpl: impl,
    });

    await expect(client.get('/api/logs/client')).rejects.toBeInstanceOf(HttpStatusError);
    expect(onAuthLost).toHaveBeenCalledTimes(1);
  });

  it('恢复成功但重试仍是 401：不再刷新第二次，且通知会话失效', async () => {
    const { impl, calls } = sequencedFetch([UNAUTHORIZED, UNAUTHORIZED]);
    const onUnauthorized = vi.fn(async () => true);
    const onAuthLost = vi.fn();
    const client = createHttpClient({
      ...silent,
      accessTokenProvider: () => 'token-a',
      onUnauthorized,
      onAuthLost,
      fetchImpl: impl,
    });

    await expect(client.get('/api/logs/client')).rejects.toMatchObject({ status: 401 });
    expect(calls).toHaveLength(2);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
    expect(onAuthLost).toHaveBeenCalledTimes(1);
  });

  it('恢复钩子自身抛错时按"未恢复"处理，抛出的是原始 401 而不是钩子的错', async () => {
    const { impl } = sequencedFetch([UNAUTHORIZED]);
    const client = createHttpClient({
      ...silent,
      accessTokenProvider: () => 'token-a',
      onUnauthorized: () => {
        throw new Error('刷新接口挂了');
      },
      fetchImpl: impl,
    });

    await expect(client.get('/api/logs/client')).rejects.toMatchObject({
      status: 401,
      kind: 'http',
    });
  });

  it('外部取消导致的失败不触发恢复', async () => {
    const controller = new AbortController();
    const { impl } = sequencedFetch([UNAUTHORIZED]);
    const onUnauthorized = vi.fn(async () => true);
    const client = createHttpClient({
      ...silent,
      accessTokenProvider: () => 'token-a',
      onUnauthorized,
      fetchImpl: impl,
    });

    controller.abort();
    await expect(
      client.get('/api/logs/client', { signal: controller.signal }),
    ).rejects.toBeDefined();
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('恢复后重试的请求同样解开统一响应包装', async () => {
    const { impl } = sequencedFetch([UNAUTHORIZED, { status: 200, body: envelope([1, 2, 3]) }]);
    const client = createHttpClient({
      ...silent,
      accessTokenProvider: () => 'token-a',
      onUnauthorized: async () => true,
      fetchImpl: impl,
    });

    await expect(client.get<number[]>('/api/logs/client')).resolves.toEqual([1, 2, 3]);
  });
});

describe('createHttpClient · 请求方法与选项透传（回归）', () => {
  it('恢复重试保留原有的方法与请求体', async () => {
    const { impl, calls } = sequencedFetch([UNAUTHORIZED, { status: 200, body: envelope(null) }]);
    const client = createHttpClient({
      ...silent,
      accessTokenProvider: () => 'token-a',
      onUnauthorized: async () => true,
      fetchImpl: impl,
    });

    await client.post('/api/events/client', { events: [] });

    expect(calls.map((call) => call.method)).toEqual(['POST', 'POST'] satisfies HttpMethod[]);
  });
});
