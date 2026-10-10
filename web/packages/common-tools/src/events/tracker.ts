import { sanitizeObject } from '../json';

import {
  isValidEventName,
  type EventContext,
  type EventEnvelope,
  type EventListener,
  type EventMiddleware,
  type EventPropsInput,
  type TrackOptions,
  type Tracker,
  type TrackerOptions,
} from './types';

/** 进程内单调序号，与时间、随机数一起构成事件 id */
let sequence = 0;

/**
 * 默认事件 id。
 *
 * <p>三段拼起来而不是单个随机数：时间提供可读性与大致排序，序号保证同毫秒内不撞，
 * 随机段兜住多实例/多进程同毫秒的残存可能。事件 id 是**幂等键**，撞了的后果是
 * 两条真实事件被当成一条，因此宁可贵一点。
 */
function defaultIdFactory(): string {
  sequence += 1;
  return `evt-${Date.now().toString(36)}-${sequence.toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

function createSessionId(): string {
  return `s-${Math.random().toString(36).slice(2, 10)}`;
}

/** 记录过的非法事件名上限。防的是"在循环里拼了个带参数的名字"这类刷屏 */
const MAX_REPORTED_NAMES = 50;

/**
 * 创建事件追踪器。
 *
 * <p><b>它的形态是"语义化 API + 内部总线"，而不是一个谁都能用的全局事件总线。</b>
 * 区别在意图：业务代码只调 {@link Tracker.track}，走的是"发生了什么业务动作"这个语义；
 * 订阅、中间件、多消费方这些机制是**埋点 SDK 的内部实现**，用来补齐上下文、
 * 分发到上报通道、以及在测试里捕获。
 *
 * <p>做成"万物皆事件"的通用总线看起来更灵活，代价是没人能回答"这个事件是谁发的、
 * 谁在听"，而埋点恰恰是最需要能回答这两个问题的场景。
 *
 * <p><b>本函数返回的对象在任何情况下都不向外抛异常。</b>埋点失败的严重性，
 * 远低于它打断的那段业务逻辑；因此全部异常都送到 {@link TrackerOptions.onError}。
 */
export function createTracker(options: TrackerOptions = {}): Tracker {
  const listeners = new Set<EventListener>(options.listeners ?? []);
  const middlewares: EventMiddleware[] = [...(options.middlewares ?? [])];
  const now = options.now ?? ((): number => Date.now());
  const idFactory = options.idFactory ?? defaultIdFactory;
  const reportedNames = new Set<string>();

  let context: EventContext = { sessionId: createSessionId(), ...options.context };

  const reportError = (error: unknown, stage: string): void => {
    try {
      options.onError?.(error, stage);
    } catch {
      // 异常出口自己如果抛异常，就没有出口了。这是最后一道，只能咽下
    }
  };

  const resolveContext = (): EventContext => {
    if (!options.contextProvider) return context;
    try {
      return { ...context, ...options.contextProvider() };
    } catch (error) {
      reportError(error, 'context');
      return context;
    }
  };

  const runMiddlewares = (event: EventEnvelope): EventEnvelope | null => {
    const dispatch = (index: number, current: EventEnvelope): EventEnvelope | null => {
      if (index >= middlewares.length) return current;
      const middleware = middlewares[index] as EventMiddleware;
      let called = false;
      return middleware(current, (nextEvent) => {
        // 同一个中间件调用两次 next 会产生两条并行的链，语义上说不通，
        // 且会让"丢弃"这一动作失去确定性（一条被丢、另一条继续跑）
        if (called) return current;
        called = true;
        return dispatch(index + 1, nextEvent ?? current);
      });
    };
    return dispatch(0, event);
  };

  const deliver = (event: EventEnvelope): void => {
    for (const listener of listeners) {
      // 逐个 try/catch：一个订阅者出错不能让其余订阅者收不到事件，
      // 更不能把异常抛回业务代码
      try {
        listener(event);
      } catch (error) {
        reportError(error, 'listener');
      }
    }
  };

  return {
    track(
      eventName: string,
      props?: EventPropsInput,
      options?: TrackOptions,
    ): EventEnvelope | null {
      try {
        if (!isValidEventName(eventName)) {
          // 只报一次：同一个拼错的名字会在每次交互里重复出现，刷屏会淹没真正的问题
          if (!reportedNames.has(eventName) && reportedNames.size < MAX_REPORTED_NAMES) {
            reportedNames.add(eventName);
            reportError(
              new Error(`事件名不合法，已丢弃：${eventName}（要求形如 order.submit）`),
              'validate',
            );
          }
          return null;
        }

        const ctx = resolveContext();
        const envelope: EventEnvelope = {
          eventId: idFactory(),
          eventName,
          clientTime: now(),
          sessionId: ctx.sessionId,
          userId: ctx.userId,
          deviceId: ctx.deviceId,
          route: ctx.route,
          release: ctx.release,
          // 属性里出现函数、循环引用、DOM 节点是常事，而不修剪的话 JSON.stringify 会抛错 ——
          // 一条脏属性会让整批上报失败，另外 499 条一起丢
          props: sanitizeObject(props),
          // 缺省 1 而不是 undefined：让"没有显式声明过版本"与"版本是 1"区分开没有意义，
          // 而列里全是 NULL 时，没人知道该从哪个数字开始递增
          propsVersion: options?.propsVersion ?? 1,
        };

        const finalEvent = runMiddlewares(envelope);
        // 中间件返回 null 即"这条不要了"（脱敏、采样、屏蔽都在这里表达）
        if (!finalEvent) return null;

        deliver(finalEvent);
        return finalEvent;
      } catch (error) {
        reportError(error, 'track');
        return null;
      }
    },

    subscribe(listener: EventListener): () => void {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },

    use(middleware: EventMiddleware): () => void {
      middlewares.push(middleware);
      return () => {
        const index = middlewares.indexOf(middleware);
        if (index >= 0) middlewares.splice(index, 1);
      };
    },

    updateContext(patch: Partial<EventContext>): void {
      context = { ...context, ...patch };
    },

    getContext(): Readonly<EventContext> {
      return { ...context };
    },

    clearUser(): void {
      const { userId: _dropped, ...rest } = context;
      context = rest;
    },
  };
}
