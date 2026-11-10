import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, createHttpClient } from './client';

/** 构造统一信封 JSON 响应 */
function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/** 断言取用的 fetch 调用（[url, init] 二元组） */
type FetchCall = [string, RequestInit];

/** 当前测试的 cookie 值（模拟 document.cookie） */
let cookieJar = '';

/**
 * 读取第 n 次 fetch 调用的 URL 与请求头（Headers 实例）。
 */
function callAt(
  fetchMock: ReturnType<typeof vi.fn>,
  index: number,
): { url: string; headers: Headers } {
  const [url, init] = (fetchMock.mock.calls as unknown as FetchCall[])[index];
  return { url, headers: init.headers as Headers };
}

describe('createHttpClient', () => {
  beforeEach(() => {
    cookieJar = 'whale_csrf=csrf-123';
    // 纯库单测不引入 jsdom：仅实现 client.ts 实际读取的 document.cookie
    (globalThis as unknown as { document: { cookie: string } }).document = {
      get cookie() {
        return cookieJar;
      },
    };
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('GET 注入 Bearer/Trace-Id、忽略空查询参数、不带 CSRF', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ code: 0, message: 'ok', data: { id: 7 } }));
    vi.stubGlobal('fetch', fetchMock);
    const client = createHttpClient({ baseUrl: 'http://api', getToken: () => 'token-abc' });

    const data = await client.get<{ id: number }>('/doc/meta', {
      page: 2,
      empty: '',
      missing: undefined,
    });

    expect(data).toEqual({ id: 7 });
    const { url, headers } = callAt(fetchMock, 0);
    expect(url).toBe('http://api/doc/meta?page=2');
    expect(headers.get('Authorization')).toBe('Bearer token-abc');
    expect(headers.get('X-CSRF-Token')).toBeNull();
    expect(headers.get('X-Trace-Id')).toMatch(/^[0-9a-f]{32}$/);
    expect((fetchMock.mock.calls as unknown as FetchCall[])[0][1].credentials).toBe('include');
  });

  it('非 GET 请求回传 CSRF 头并 JSON 序列化请求体', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ code: 0, message: 'ok', data: null }));
    vi.stubGlobal('fetch', fetchMock);
    const client = createHttpClient({ baseUrl: '', getToken: () => 'tk' });

    await client.post('/doc/space', { name: 'M1' });

    const { headers } = callAt(fetchMock, 0);
    expect(headers.get('X-CSRF-Token')).toBe('csrf-123');
    expect(headers.get('Content-Type')).toBe('application/json');
    expect((fetchMock.mock.calls as unknown as FetchCall[])[0][1].body).toBe('{"name":"M1"}');
  });

  it('FormData 请求体原样透传且不覆盖 multipart Content-Type', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ code: 0, message: 'ok', data: null }));
    vi.stubGlobal('fetch', fetchMock);
    const client = createHttpClient({ baseUrl: '' });
    const form = new FormData();
    form.append('file', new Blob(['abc'], { type: 'text/plain' }), 'a.txt');

    await client.post('/pdm/document/1/versions', form);

    const init = (fetchMock.mock.calls as unknown as FetchCall[])[0][1];
    expect(init.body).toBe(form);
    expect((init.headers as Headers).get('Content-Type')).toBeNull();
    expect((init.headers as Headers).get('X-CSRF-Token')).toBe('csrf-123');
  });

  it('CSRF Cookie 缺失时不注入该请求头', async () => {
    cookieJar = '';
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ code: 0, message: 'ok', data: null }));
    vi.stubGlobal('fetch', fetchMock);
    const client = createHttpClient({});

    await client.put('/doc/page', { title: 'x' });

    expect(callAt(fetchMock, 0).headers.get('X-CSRF-Token')).toBeNull();
  });

  it('业务码非 0 时抛 ApiError（即使 HTTP 200）', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ code: 400, message: '参数错误', data: null }));
    vi.stubGlobal('fetch', fetchMock);
    const client = createHttpClient({});

    await expect(client.get('/doc/meta')).rejects.toMatchObject({
      name: 'ApiError',
      code: 400,
      message: '参数错误',
    });
  });

  it('401 时单飞续期并重放一次原请求', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ code: 401, message: '过期', data: null }, 401))
      .mockResolvedValueOnce(
        jsonResponse({ code: 0, message: 'ok', data: { accessToken: 'new-token' } }),
      )
      .mockResolvedValueOnce(jsonResponse({ code: 0, message: 'ok', data: { ok: true } }));
    vi.stubGlobal('fetch', fetchMock);
    const saveLogin = vi.fn();
    const client = createHttpClient({
      baseUrl: 'http://api',
      getToken: () => 'old-token',
      saveLogin,
    });

    const data = await client.get<{ ok: boolean }>('/doc/meta');

    expect(data).toEqual({ ok: true });
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(saveLogin).toHaveBeenCalledWith({ accessToken: 'new-token' });
    const refresh = callAt(fetchMock, 1);
    expect(refresh.url).toBe('http://api/auth/refresh');
    expect(refresh.headers.get('X-CSRF-Token')).toBe('csrf-123');
  });

  it('并发 401 只触发一次刷新请求（single-flight）', async () => {
    // 用闸门延迟刷新响应，确保两个并发请求都在刷新进行中进入 single-flight
    let releaseRefresh: () => void = () => undefined;
    const refreshGate = new Promise<void>((resolve) => {
      releaseRefresh = resolve;
    });
    const fetchMock = vi.fn().mockImplementation(async (input: string) => {
      if (input.includes('/v1/auth/refresh')) {
        await refreshGate;
        return jsonResponse({ code: 0, message: 'ok', data: { accessToken: 'new-token' } });
      }
      return jsonResponse({ code: 401, message: '过期', data: null }, 401);
    });
    vi.stubGlobal('fetch', fetchMock);
    const client = createHttpClient({
      baseUrl: 'http://api',
      getToken: () => 'old-token',
      saveLogin: vi.fn(),
    });

    // 两个请求各自 401 后共享同一次刷新；刷新后重放仍 401 → 各自抛出
    const pending = Promise.allSettled([client.get('/doc/a'), client.get('/doc/b')]);
    await new Promise((resolve) => setTimeout(resolve, 0));
    releaseRefresh();
    const results = await pending;

    expect(results.every((item) => item.status === 'rejected')).toBe(true);
    const refreshCalls = (fetchMock.mock.calls as unknown as FetchCall[]).filter(([url]) =>
      url.includes('/v1/auth/refresh'),
    );
    expect(refreshCalls).toHaveLength(1);
  });

  it('登录路径 401 不触发续期，直接清态并抛出', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ code: 401, message: '账号或口令错误', data: null }, 401));
    vi.stubGlobal('fetch', fetchMock);
    const clearAuth = vi.fn();
    const onUnauthorized = vi.fn();
    const client = createHttpClient({
      baseUrl: 'http://api',
      saveLogin: vi.fn(),
      clearAuth,
      onUnauthorized,
    });

    await expect(client.post('/v1/auth/login', { username: 'u' })).rejects.toMatchObject({
      name: 'ApiError',
      code: 401,
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(clearAuth).toHaveBeenCalledTimes(1);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it('未配置 saveLogin 时 401 直接抛出 ApiError', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ code: 401, message: '登录已失效', data: null }, 401));
    vi.stubGlobal('fetch', fetchMock);
    const client = createHttpClient({ baseUrl: 'http://api' });

    await expect(client.get('/doc/meta')).rejects.toBeInstanceOf(ApiError);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('下载解析 Content-Disposition 文件名并解码', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(new Blob(['a,b'], { type: 'text/csv' }), {
        status: 200,
        headers: { 'Content-Disposition': 'attachment; filename="%E6%8A%A5%E8%A1%A8.csv"' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const client = createHttpClient({ baseUrl: 'http://api', getToken: () => 'tk' });

    const result = await client.downloadBlob('/doc/export', { spaceId: 1 });

    expect(result.filename).toBe('报表.csv');
    expect(await result.blob.text()).toBe('a,b');
    expect(callAt(fetchMock, 0).url).toBe('http://api/doc/export?spaceId=1');
  });

  it('SSE 按帧解析 event 与 JSON data，非 JSON 保持原文', async () => {
    const encoder = new TextEncoder();
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(
          encoder.encode('event: meta\ndata: {"a":1}\n\nevent: delta\ndata: 纯文本\n\n'),
        );
        controller.close();
      },
    });
    const fetchMock = vi
      .fn()
      .mockResolvedValue(
        new Response(stream, { status: 200, headers: { 'Content-Type': 'text/event-stream' } }),
      );
    vi.stubGlobal('fetch', fetchMock);
    const client = createHttpClient({ baseUrl: 'http://api', getToken: () => 'tk' });
    const events: Array<[string, unknown]> = [];

    await client.streamSse('/agent/chat/stream', { q: 'hi' }, (event, data) =>
      events.push([event, data]),
    );

    expect(events).toEqual([
      ['meta', { a: 1 }],
      ['delta', '纯文本'],
    ]);
    expect(callAt(fetchMock, 0).headers.get('X-CSRF-Token')).toBe('csrf-123');
  });
});
