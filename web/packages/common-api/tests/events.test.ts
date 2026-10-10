import { describe, expect, expectTypeOf, it, vi } from 'vitest';

import { createApiClient } from '../src/client';
import type {
  ClientEventItem,
  ClientEventPage,
  ClientEventQuery,
  ClientEventVO,
  EventApi,
} from '../src/events';
import type { JsonObject } from '../src/types';

function fakeResponse(body: unknown, status = 200): Response {
  return {
    status,
    statusText: '',
    ok: status >= 200 && status < 300,
    headers: { get: () => null },
    text: async () => (body === undefined ? '' : JSON.stringify(body)),
  } as unknown as Response;
}

function okFetch(data: unknown) {
  return vi.fn(async () =>
    fakeResponse({ code: 0, message: '成功', data, timestamp: 1 }),
  ) as unknown as typeof fetch;
}

/** 取第 n 次 fetch 调用的 [url, init] */
function callOf(fetchImpl: typeof fetch, index = 0): [string, RequestInit] {
  const calls = (fetchImpl as unknown as { mock: { calls: [string, RequestInit][] } }).mock.calls;
  return calls[index];
}

describe('createApiClient · 事件域接口调用', () => {
  it('分页查询拼出正确的路径与方法，并解开响应包装', async () => {
    const fetchImpl = okFetch({ records: [], total: 0, page: 1, size: 20, pages: 0 });
    const api = createApiClient({ fetchImpl, logger: null });

    const result = await api.event.page({ eventName: 'order.submit', page: 1 });

    const [url, init] = callOf(fetchImpl);
    expect(init.method).toBe('GET');
    expect(url).toBe('/api/events/client?eventName=order.submit&page=1');
    expect(result).toEqual({ records: [], total: 0, page: 1, size: 20, pages: 0 });
  });

  it('上报走 POST 并序列化请求体', async () => {
    const fetchImpl = okFetch({ accepted: 2, rejected: 0 });
    const api = createApiClient({ fetchImpl, logger: null });

    const result = await api.event.report({
      app: 'demo',
      events: [{ eventId: 'e-1', eventName: 'order.submit', timestamp: 1 }],
    });

    const [url, init] = callOf(fetchImpl);
    expect(init.method).toBe('POST');
    expect(url).toBe('/api/events/client');
    expect(JSON.parse(String(init.body))).toMatchObject({ app: 'demo' });
    expect(result.accepted).toBe(2);
  });

  it('详情与聚合各走自己的路径', async () => {
    const fetchImpl = okFetch({ total: 0, names: [] });
    const api = createApiClient({ fetchImpl, logger: null });

    // 聚合只接受 app 与时间窗：它按事件名**分组**返回分布，
    // 再传一个 eventName 过滤是自相矛盾的。（契约如此，不是遗漏）
    await api.event.stats({ app: 'demo' });
    expect(callOf(fetchImpl)[0]).toBe('/api/events/client/stats?app=demo');

    await api.event.detail(7);
    expect(callOf(fetchImpl, 1)[0]).toBe('/api/events/client/7');
  });

  it('后端不可用时抛出的错误带 url 与分类，便于定位', async () => {
    const fetchImpl = vi.fn(async () => {
      throw new TypeError('fetch failed');
    }) as unknown as typeof fetch;
    const api = createApiClient({ fetchImpl, logger: null });

    const error = (await api.event.page().catch((e: unknown) => e)) as {
      kind: string;
      url: string;
      retryable: boolean;
    };

    expect(error.kind).toBe('network');
    expect(error.url).toBe('/api/events/client');
    expect(error.retryable).toBe(true);
  });
});

describe('common-api · 事件契约即类型', () => {
  it('查询条件的类型由契约生成，时间字段是数字而非字符串', () => {
    expectTypeOf<ClientEventQuery>().toHaveProperty('page');
    expectTypeOf<ClientEventQuery>().toHaveProperty('size');
    expectTypeOf<ClientEventQuery>().toHaveProperty('eventName');

    // 与日志域同一条陷阱：springdoc 在**查询参数**位置会以 @Schema 注解为准重建 schema，
    // 而注解的 type 默认为空串，字段会被"推平"成 string。若某天有人把
    // 事件查询 DTO 上的 `type = "integer"` 删掉，这一行会失败。
    expectTypeOf<ClientEventQuery['startTime']>().toEqualTypeOf<number | undefined>();
  });

  it('出参模型按契约生成，字段与后端一致', () => {
    expectTypeOf<ClientEventVO>().toHaveProperty('serverTime');
    expectTypeOf<ClientEventVO>().toHaveProperty('props');
    expectTypeOf<ClientEventVO>().toHaveProperty('deviceId');
    expectTypeOf<ClientEventVO>().toHaveProperty('propsVersion');
    expectTypeOf<ClientEventVO['eventName']>().toEqualTypeOf<string | undefined>();
  });

  it('分页记录的元素与单条事件是同一种类型', () => {
    // 与日志域同样的裂缝：契约生成的 `PageResult<ClientEventVO>` 里的元素
    // 与外面对外导出的 ClientEventVO 曾不是同一个类型。钉住它。
    expectTypeOf<ClientEventPage['records']>().toEqualTypeOf<ClientEventVO[]>();
  });

  it('事件属性被收紧为 JSON 值，而不是放任 unknown', () => {
    // 事件属性是"用户随手塞进来的对象"，正是最需要挡住函数 / Symbol / DOM 节点的地方 ——
    // 一条不可序列化的属性会让整批 500 条上报一起失败。
    expectTypeOf<ClientEventVO['props']>().toEqualTypeOf<JsonObject | undefined>();
    expectTypeOf<ClientEventItem['props']>().toEqualTypeOf<JsonObject | undefined>();
  });

  it('上报条目的形状与前端 EventEnvelope 对齐', () => {
    const item: ClientEventItem = {
      eventId: 'e-1',
      eventName: 'order.submit',
      sessionId: 's-1',
      timestamp: 1,
      props: { orderId: 'SO-1', amount: 99 },
    };
    expectTypeOf<EventApi['report']>().toBeFunction();
    expect(item.eventName).toBe('order.submit');
  });
});
