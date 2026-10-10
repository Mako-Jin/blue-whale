import { describe, expect, it, vi } from 'vitest';

import { createTracker } from '../src/events';
import type { EventEnvelope, TrackerOptions } from '../src/events';
import { isValidEventName, sanitize, sanitizeObject } from '../src/index';

/** 造一个可控的追踪器：固定时钟与自增 id，让断言稳定 */
function createTestTracker(overrides: Partial<TrackerOptions> = {}) {
  let clock = 1_700_000_000_000;
  let seq = 0;
  const onError = vi.fn();
  const tracker = createTracker({
    now: () => (clock += 1),
    idFactory: () => `e-${(seq += 1)}`,
    onError,
    context: { sessionId: 's-fixed' },
    ...overrides,
  });
  return { tracker, onError };
}

describe('sanitize · 把任意值修剪成可序列化形态', () => {
  it('保留 JSON 原生值', () => {
    expect(sanitize('a')).toBe('a');
    expect(sanitize(1)).toBe(1);
    expect(sanitize(true)).toBe(true);
    expect(sanitize(null)).toBeNull();
    expect(sanitize(undefined)).toBeNull();
  });

  it('函数与 Symbol 转成可读标记，而不是让整批序列化失败', () => {
    // 这是本函数存在的头号理由：JSON.stringify 遇到函数/ Symbol 会抛错，
    // 而"一条脏数据炸掉整批上报"是真实发生过的故障模式
    expect(sanitize(() => 1)).toBe('[function]');
    expect(sanitize(Symbol('x'))).toBe('[symbol]');
  });

  it('BigInt 转字符串：JSON.stringify 对它会直接抛错', () => {
    expect(sanitize(10n ** 20n)).toBe('100000000000000000000');
  });

  it('非有限数转字符串，不静默变成 null', () => {
    // JSON.stringify(NaN) 得到 "null"，那会让人以为"这个字段本来就没值"
    expect(sanitize(Number.NaN)).toBe('NaN');
    expect(sanitize(Number.POSITIVE_INFINITY)).toBe('Infinity');
  });

  it('Error 展开成可读结构', () => {
    expect(sanitize(new TypeError('炸了'))).toMatchObject({
      name: 'TypeError',
      message: '炸了',
    });
  });

  it('循环引用不会无限递归', () => {
    const cyclic: Record<string, unknown> = { name: 'a' };
    cyclic.self = cyclic;
    expect(() => JSON.stringify(sanitize(cyclic))).not.toThrow();
  });

  it('超过深度上限的嵌套被截断，而不是无限展开', () => {
    const deep = { a: { b: { c: { d: { e: { f: '太深了' } } } } } };
    expect(JSON.stringify(sanitize(deep))).toContain('层级过深');
  });

  it('sanitizeObject 对空入参返回 undefined，而不是空对象', () => {
    expect(sanitizeObject(undefined)).toBeUndefined();
    expect(sanitizeObject({})).toEqual({});
  });
});

describe('事件名校验', () => {
  it('接受小写点分的至少两段格式', () => {
    for (const name of ['order.submit', 'page.view', 'log.console.generate', 'a.b_c.d1']) {
      expect(isValidEventName(name)).toBe(true);
    }
  });

  it('拒绝大写、单段、空串与非法首字符', () => {
    for (const name of [
      'Order.Submit',
      'submit',
      '',
      '.submit',
      'order.',
      '1.order',
      'order..submit',
    ]) {
      expect(isValidEventName(name)).toBe(false);
    }
  });
});

describe('createTracker · 语义化埋点 API', () => {
  it('track 补齐信封字段，业务只需给事件名与属性', () => {
    const { tracker } = createTestTracker();

    const event = tracker.track('order.submit', { orderId: 'SO-1', amount: 99 });

    expect(event).toMatchObject({
      eventId: 'e-1',
      eventName: 'order.submit',
      sessionId: 's-fixed',
      props: { orderId: 'SO-1', amount: 99 },
    });
    expect(typeof event?.clientTime).toBe('number');
  });

  it('事件名不合法时丢弃并只报一次，不刷屏', () => {
    const { tracker, onError } = createTestTracker();

    expect(tracker.track('Order.Submit')).toBeNull();
    expect(tracker.track('Order.Submit')).toBeNull();
    expect(tracker.track('Order.Submit')).toBeNull();

    // 三次调用只报一次：同一个拼错的名字会在每次交互里重复出现，
    // 每次都报会把真正的问题淹掉
    expect(onError).toHaveBeenCalledTimes(1);
    expect(onError.mock.calls[0]?.[1]).toBe('validate');
  });

  it('上下文每次发布时重新求值，登录后的事件立刻带上身份', () => {
    // 用可变容器而不是 `let userId`：prefer-const 会把"声明后仅赋值一次"的 let
    // 判为应为 const —— 但本用例要表达的恰恰是"身份在运行期从无到有"，
    // 改成 const 就把被测行为改掉了
    const state: { userId?: string } = {};
    const { tracker } = createTestTracker({ contextProvider: () => ({ userId: state.userId }) });

    const before = tracker.track('page.view');
    expect(before?.userId).toBeUndefined();

    state.userId = 'u-42';

    const after = tracker.track('page.view');
    expect(after?.userId).toBe('u-42');
  });

  it('updateContext 合并而不是整体替换', () => {
    const { tracker } = createTestTracker();

    tracker.updateContext({ route: '/orders' });

    expect(tracker.getContext()).toMatchObject({ sessionId: 's-fixed', route: '/orders' });
  });

  it('clearUser 清掉登录身份但保留会话与设备标识', () => {
    const { tracker } = createTestTracker();
    tracker.updateContext({ userId: 'u-1', deviceId: 'd-1' });

    tracker.clearUser();

    const context = tracker.getContext();
    expect(context.userId).toBeUndefined();
    expect(context.deviceId).toBe('d-1');
    expect(context.sessionId).toBe('s-fixed');
  });

  it('属性里的函数被洗成标记，不会让序列化抛错', () => {
    const { tracker } = createTestTracker();

    const event = tracker.track('order.submit', { handler: () => 1, id: 'SO-1' });

    expect(event?.props).toEqual({ handler: '[function]', id: 'SO-1' });
    expect(() => JSON.stringify(event)).not.toThrow();
  });
});

describe('createTracker · 内部总线', () => {
  it('多个订阅者都收到同一个事件', () => {
    const { tracker } = createTestTracker();
    const a = vi.fn();
    const b = vi.fn();
    tracker.subscribe(a);
    tracker.subscribe(b);

    const event = tracker.track('page.view');

    expect(a).toHaveBeenCalledWith(event);
    expect(b).toHaveBeenCalledWith(event);
  });

  it('取消订阅后不再收到', () => {
    const { tracker } = createTestTracker();
    const listener = vi.fn();
    const unsubscribe = tracker.subscribe(listener);

    tracker.track('page.view');
    unsubscribe();
    tracker.track('page.view');

    expect(listener).toHaveBeenCalledTimes(1);
  });

  it('某一个订阅者抛错不影响其余订阅者，也不把异常抛回业务代码', () => {
    const { tracker, onError } = createTestTracker();
    const broken = vi.fn(() => {
      throw new Error('订阅者内部炸了');
    });
    const healthy = vi.fn();
    tracker.subscribe(broken);
    tracker.subscribe(healthy);

    expect(() => tracker.track('page.view')).not.toThrow();
    expect(healthy).toHaveBeenCalledTimes(1);
    expect(onError).toHaveBeenCalledWith(expect.any(Error), 'listener');
  });

  it('中间件可以改写事件（补齐字段）', () => {
    const { tracker } = createTestTracker();
    tracker.use((event, next) => next({ ...event, route: '/from-middleware' }));

    const event = tracker.track('page.view');

    expect(event?.route).toBe('/from-middleware');
  });

  it('中间件返回 null 即丢弃，订阅者收不到', () => {
    const { tracker } = createTestTracker();
    const listener = vi.fn();
    tracker.subscribe(listener);
    // 这是一条"屏蔽敏感事件"的典型写法
    tracker.use((event, next) => (event.eventName === 'page.view' ? null : next(event)));

    expect(tracker.track('page.view')).toBeNull();
    expect(listener).not.toHaveBeenCalled();

    expect(tracker.track('order.submit')).not.toBeNull();
    expect(listener).toHaveBeenCalledTimes(1);
  });

  it('移除中间件后不再生效', () => {
    const { tracker } = createTestTracker();
    const remove = tracker.use((event, next) => next({ ...event, route: '/mw' }));

    expect(tracker.track('page.view')?.route).toBe('/mw');
    remove();
    expect(tracker.track('page.view')?.route).toBeUndefined();
  });

  it('订阅者拿到的事件与 track 的返回值是同一个对象', () => {
    const { tracker } = createTestTracker();
    let seen: EventEnvelope | null = null;
    tracker.subscribe((event) => {
      seen = event;
    });

    const returned = tracker.track('page.view');

    expect(seen).toBe(returned);
  });
});
