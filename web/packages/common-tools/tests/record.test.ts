import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { configureLogger, createLogger, resetLogger, setLoggerSink } from '../src/logger';
import type { LogRecord } from '../src/logger';

/** 收集结构化记录到数组，并静音控制台避免污染测试输出。 */
function collector(): { records: LogRecord[]; handler: (record: LogRecord) => void } {
  const records: LogRecord[] = [];
  setLoggerSink(() => undefined);
  return { records, handler: (record) => records.push(record) };
}

beforeEach(() => {
  resetLogger();
});

afterEach(() => {
  resetLogger();
});

describe('LogRecord 结构化记录', () => {
  it('携带级别、命名空间、时间戳、消息、数据与上下文', () => {
    const { records, handler } = collector();
    const before = Date.now();

    createLogger('app › auth', { onRecord: handler, context: { user: 'u1' } }).info('login ok', {
      id: 7,
    });
    const after = Date.now();

    expect(records).toHaveLength(1);
    const record = records[0];
    expect(record.level).toBe('info');
    expect(record.namespace).toBe('app › auth');
    expect(record.message).toBe('login ok');
    expect(record.data).toEqual([{ id: 7 }]);
    expect(record.context).toEqual({ user: 'u1' });
    expect(record.timestamp).toBeGreaterThanOrEqual(before);
    expect(record.timestamp).toBeLessThanOrEqual(after);
  });

  it('级别保真：info 与 success 在记录里分得开，而 sink 做不到', () => {
    const { records, handler } = collector();
    const log = createLogger('t', { onRecord: handler });

    log.info('i');
    log.success('s');
    expect(records.map((record) => record.level)).toEqual(['info', 'success']);

    // 对照：同两条日志走 sink 时都被压成 'log'，消费方无从区分
    const methods: string[] = [];
    setLoggerSink((method) => {
      methods.push(method);
    });
    log.info('i');
    log.success('s');
    expect(methods).toEqual(['log', 'log']);
  });

  it('低于阈值的日志既不输出、也不产生记录', () => {
    const { records, handler } = collector();
    const log = createLogger('t', { level: 'warn', onRecord: handler });

    log.debug('d');
    log.info('i');
    log.warn('w');

    expect(records.map((record) => record.level)).toEqual(['warn']);
  });

  it('上报元数据随记录发出', () => {
    const { records, handler } = collector();

    createLogger('t', {
      onRecord: handler,
      traceId: 'trace-1',
      sessionId: 'sess-1',
      route: '/orders/42',
      release: '1.2.3',
    }).error('boom');

    const record = records[0];
    expect(record.traceId).toBe('trace-1');
    expect(record.sessionId).toBe('sess-1');
    expect(record.route).toBe('/orders/42');
    expect(record.release).toBe('1.2.3');
  });

  it('未设置元数据时字段为 undefined，不产生空壳对象', () => {
    const { records, handler } = collector();
    createLogger('t', { onRecord: handler }).info('m');

    const record = records[0];
    expect(record.context).toBeUndefined();
    expect(record.traceId).toBeUndefined();
    expect(record.sessionId).toBeUndefined();
    expect(record.route).toBeUndefined();
    expect(record.release).toBeUndefined();
  });

  it('withContext 的上下文进入记录，且记录持有的是副本', () => {
    const { records, handler } = collector();
    const log = createLogger('t', { onRecord: handler, context: { traceId: 't1' } });

    log.withContext({ span: 'db' }).info('query');
    log.info('plain');

    expect(records[0].context).toEqual({ traceId: 't1', span: 'db' });
    expect(records[1].context).toEqual({ traceId: 't1' });

    // 篡改记录里的 context 不应污染后续日志
    const tamper = records[0].context as Record<string, unknown>;
    tamper.span = 'tampered';
    log.info('again');
    expect(records[2].context).toEqual({ traceId: 't1' });
  });

  it('非记录方法（table / divider / clear）不触发 onRecord', () => {
    const { records, handler } = collector();
    setLoggerSink(() => undefined);
    const log = createLogger('t', { onRecord: handler });

    log.table([{ a: 1 }]);
    log.divider();
    log.clear();

    expect(records).toEqual([]);
  });

  it('全局 onRecord 对「先创建」的实例同样生效', () => {
    const { records, handler } = collector();
    const log = createLogger('t');
    configureLogger({ onRecord: handler });

    log.info('late');

    expect(records).toHaveLength(1);
  });

  it('实例 onRecord 覆盖全局 onRecord', () => {
    const global = collector();
    const local = collector();
    configureLogger({ onRecord: global.handler });

    createLogger('a').info('via-global');
    createLogger('b', { onRecord: local.handler }).info('via-local');

    expect(global.records.map((record) => record.namespace)).toEqual(['a']);
    expect(local.records.map((record) => record.namespace)).toEqual(['b']);
  });

  it('onRecord 抛错被静默，不阻断 console 输出（防日志雪崩）', () => {
    const methods: string[] = [];
    setLoggerSink((method) => {
      methods.push(method);
    });
    const log = createLogger('t', {
      colorize: false,
      showTimestamp: false,
      onRecord: () => {
        throw new Error('上报失败');
      },
    });

    expect(() => log.error('boom')).not.toThrow();
    expect(methods).toEqual(['error']);
  });

  it('data 保持原始形态，对象不被 stringify；Error 消息被提取', () => {
    const { records, handler } = collector();
    const log = createLogger('t', { onRecord: handler });

    const payload = { nested: { a: 1 } };
    log.info('x', payload);
    expect(records[0].data[0]).toBe(payload);

    const error = new Error('kaboom');
    log.error(error);
    const last = records[1];
    expect(last.level).toBe('error');
    expect(last.message).toBe('kaboom');
    expect(last.data[0]).toBe(error);
  });

  it('log(level, ...) 与 perf 也走结构化记录', () => {
    const { records, handler } = collector();
    // 用 trace 阈值，否则 debug 会被默认阈值压掉
    const log = createLogger('t', { level: 'trace', onRecord: handler });

    log.log('debug', 'dynamic');
    log.perf('render', 12, 'ms');

    expect(records.map((record) => record.level)).toEqual(['debug', 'perf']);
    expect(records[1].message).toBe('render: 12ms');
  });
});
