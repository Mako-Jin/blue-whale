import { describe, expect, expectTypeOf, it, vi } from 'vitest';

import { createApiClient } from '../src/client';
import type { ClientLogItem, ClientLogPage, ClientLogQuery, ClientLogVO, LogApi } from '../src/log';
import type { JsonArray, JsonObject } from '../src/types';

function fakeResponse(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  const map = new Map(Object.entries(headers).map(([k, v]) => [k.toLowerCase(), v]));
  return {
    status,
    statusText: '',
    ok: status >= 200 && status < 300,
    headers: { get: (name: string) => map.get(name.toLowerCase()) ?? null },
    text: async () => (body === undefined ? '' : JSON.stringify(body)),
  } as unknown as Response;
}

function okFetch(data: unknown, headers?: Record<string, string>) {
  return vi.fn(async () =>
    fakeResponse({ code: 0, message: '成功', data, timestamp: 1 }, 200, headers),
  ) as unknown as typeof fetch;
}

/** 取第 n 次 fetch 调用的 [url, init] */
function callOf(fetchImpl: typeof fetch, index = 0): [string, RequestInit] {
  const calls = (fetchImpl as unknown as { mock: { calls: [string, RequestInit][] } }).mock.calls;
  return calls[index];
}

describe('createApiClient · 契约推导的接口调用', () => {
  it('分页查询拼出正确的路径与方法，并解开响应包装', async () => {
    const fetchImpl = okFetch({ records: [], total: 0, page: 1, size: 20, pages: 0 });
    const api = createApiClient({ fetchImpl, logger: null });

    const result = await api.log.page({ level: 'error', page: 1 });

    const [url, init] = callOf(fetchImpl);
    expect(init.method).toBe('GET');
    expect(url).toBe('/api/logs/client?level=error&page=1');
    // 解包后拿到的是 data 本身，而不是 { code, message, data }
    expect(result).toEqual({ records: [], total: 0, page: 1, size: 20, pages: 0 });
  });

  it('上报走 POST 并序列化请求体', async () => {
    const fetchImpl = okFetch({ accepted: 2, rejected: 0 });
    const api = createApiClient({ fetchImpl, logger: null });

    const result = await api.log.report({
      app: 'demo',
      logs: [{ level: 'info', message: '第一条' }],
    });

    const [url, init] = callOf(fetchImpl);
    expect(init.method).toBe('POST');
    expect(url).toBe('/api/logs/client');
    expect(JSON.parse(String(init.body))).toMatchObject({ app: 'demo' });
    expect(result.accepted).toBe(2);
  });

  it('链路标识里的特殊字符被编码，不会把请求打到别的接口上', async () => {
    const fetchImpl = okFetch([]);
    const api = createApiClient({ fetchImpl, logger: null });

    await api.log.listByTraceId('a/b:c d');

    expect(callOf(fetchImpl)[0]).toBe('/api/logs/client/trace/a%2Fb%3Ac%20d');
  });

  it('健康检查标记为静默：后端没起时不往控制台刷错误', async () => {
    const onError = vi.fn();
    const fetchImpl = vi.fn(async () => {
      throw new TypeError('fetch failed');
    }) as unknown as typeof fetch;
    const api = createApiClient({ fetchImpl, logger: null, onError });

    await expect(api.system.health()).rejects.toThrow();

    // onError 仍被调用（调用方要能感知失败），只是不写日志
    expect(onError).toHaveBeenCalledTimes(1);
  });

  it('后端不可用时抛出的错误带 url 与分类，便于定位', async () => {
    const fetchImpl = vi.fn(async () => {
      throw new TypeError('fetch failed');
    }) as unknown as typeof fetch;
    const api = createApiClient({ fetchImpl, logger: null });

    const error = (await api.log.page().catch((e: unknown) => e)) as {
      kind: string;
      url: string;
      retryable: boolean;
    };

    expect(error.kind).toBe('network');
    expect(error.url).toBe('/api/logs/client');
    expect(error.retryable).toBe(true);
  });
});

describe('common-api · 契约即类型', () => {
  it('查询条件的类型由契约生成，含继承而来的 page / size', () => {
    expectTypeOf<ClientLogQuery>().toHaveProperty('page');
    expectTypeOf<ClientLogQuery>().toHaveProperty('size');
    expectTypeOf<ClientLogQuery>().toHaveProperty('startTime');

    // 时间字段是数字（毫秒），不是字符串 —— 这正是当初必须给 @Schema
    // 显式声明 type 的原因，若退回 string，这一行会失败
    expectTypeOf<ClientLogQuery['startTime']>().toEqualTypeOf<number | undefined>();
  });

  it('出参模型按契约生成，字段与后端一致', () => {
    expectTypeOf<ClientLogVO>().toHaveProperty('serverTime');
    expectTypeOf<ClientLogVO>().toHaveProperty('context');
    expectTypeOf<ClientLogVO['level']>().toEqualTypeOf<string | undefined>();
  });

  it('分页结果带上服务端算好的总页数', () => {
    expectTypeOf<ClientLogPage>().toHaveProperty('pages');
    expectTypeOf<ClientLogPage>().toHaveProperty('total');
  });

  it('分页记录的元素与单条日志是同一种类型', () => {
    // 契约生成的 `PageResult<ClientLogVO>` 里的元素，与外面对外导出的 ClientLogVO
    // 曾不是同一个类型（前者未经"任意值字段"收紧）。这个裂缝在 OpenAPI 3.0 时代
    // 被 `Record<string, never>` 的可赋值性掩盖，规范升到 3.1 后立刻变成编译错误 ——
    // 从分页结果里取出的记录再也传不进任何期望 ClientLogVO 的地方。
    // 钉住它：分页里取出的记录必须能直接当 ClientLogVO 用。
    expectTypeOf<ClientLogPage['records']>().toEqualTypeOf<ClientLogVO[]>();
  });

  it('任意值字段被收紧为 JSON 值，而不是放任 unknown', () => {
    // 生成器（OpenAPI 3.1）给的是 `unknown`，过宽：函数、Symbol 都能通过编译，
    // 而它们正是 JSON.stringify 会抛错的东西 —— 一条脏数据就能让整批上报失败。
    // 这里钉住"收紧"这一事实，防止有人图省事把 JsonValue 换成 unknown。
    expectTypeOf<ClientLogVO['data']>().toEqualTypeOf<JsonArray | undefined>();
    expectTypeOf<ClientLogItem['context']>().toEqualTypeOf<JsonObject | undefined>();
  });

  it('上报条目的形状与前端 LogRecord 对齐', () => {
    const record: ClientLogItem = {
      level: 'info',
      namespace: 'app',
      timestamp: 1,
      message: 'hi',
      traceId: 't',
    };
    expectTypeOf<LogApi['report']>().toBeFunction();
    expect(record.message).toBe('hi');
  });
});
