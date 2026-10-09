import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import {
  configureLogger,
  createLogger,
  logger as rootLogger,
  resetLogger,
  setLoggerSink,
  stripFormatGuard,
} from '../src/logger';
import type { ConsoleMethod, LogSink } from '../src/logger';

interface CapturedLine {
  method: ConsoleMethod;
  args: unknown[];
  /** 第一个参数（格式串 / 文本行） */
  text: string;
}

/** 把日志捕获到内存，避免污染测试输出。 */
function capture(): CapturedLine[] {
  const lines: CapturedLine[] = [];
  const sink: LogSink = (method, args) => {
    lines.push({ method, args, text: typeof args[0] === 'string' ? args[0] : String(args[0]) });
  };
  setLoggerSink(sink);
  return lines;
}

/** 纯文本模式的基础配置：便于直接断言文本内容。 */
const plain = { showTimestamp: false, colorize: false } as const;

beforeEach(() => {
  resetLogger();
});

afterEach(() => {
  resetLogger();
});

describe('级别与过滤', () => {
  it('低于阈值的级别不输出', () => {
    const log = createLogger('t', { ...plain, level: 'warn' });
    const lines = capture();

    log.debug('d');
    log.info('i');
    log.success('s');
    log.warn('w');
    log.error('e');

    expect(lines.map((line) => line.text.trim().split(/\s+/).at(-1))).toEqual(['w', 'e']);
  });

  it('level: silent 关闭全部输出', () => {
    const log = createLogger('t', { ...plain, level: 'silent' });
    const lines = capture();

    log.error('boom');

    expect(lines).toHaveLength(0);
  });

  it('perf 默认可见，但阈值提到 warn 后一起安静下来', () => {
    const visible = capture();
    createLogger('t', plain).perf('render', 12);
    expect(visible).toHaveLength(1);

    resetLogger();
    const hidden = capture();
    createLogger('t', { ...plain, level: 'warn' }).perf('render', 12);
    expect(hidden).toHaveLength(0);
  });

  it('isEnabled / ifEnabled 可在构造昂贵参数前短路', () => {
    const log = createLogger('t', { ...plain, level: 'error' });
    let built = 0;

    expect(log.isEnabled('info')).toBe(false);
    log.ifEnabled('info', () => {
      built += 1;
    });
    log.ifEnabled('error', () => {
      built += 1;
    });

    expect(built).toBe(1);
  });
});

describe('console 方法映射', () => {
  it('每个级别落到正确的 console 方法（旧实现全部走 log）', () => {
    const log = createLogger('t', { ...plain, level: 'trace' });
    const lines = capture();

    log.trace('a');
    log.debug('b');
    log.info('c');
    log.success('d');
    log.perf('e', 1);
    log.warn('f');
    log.error('g');

    expect(lines.map((line) => line.method)).toEqual([
      'debug',
      'debug',
      'log',
      'log',
      'debug',
      'warn',
      'error',
    ]);
  });
});

describe('命名空间层级', () => {
  it('child 拼接层级路径', () => {
    const log = createLogger('app', plain).child('auth').child('login');
    expect(log.namespace).toBe('app › auth › login');
  });

  it('child 继承父级配置', () => {
    const parent = createLogger('app', { ...plain, level: 'error' });
    const child = parent.child('auth');

    expect(child.isEnabled('info')).toBe(false);
    expect(child.isEnabled('error')).toBe(true);
  });

  it('child 可覆盖父级配置', () => {
    const parent = createLogger('app', { ...plain, level: 'error' });
    const child = parent.child('auth', { level: 'debug' });

    expect(child.isEnabled('debug')).toBe(true);
    expect(parent.isEnabled('debug')).toBe(false);
  });

  it("child('') 不改变命名空间，也不产生悬空分隔符", () => {
    const parent = createLogger('app', plain);
    expect(parent.child('').namespace).toBe('app');
  });

  it('自定义分隔符生效', () => {
    const log = createLogger('app', { ...plain, namespaceSeparator: ':' });
    expect(log.child('auth').namespace).toBe('app:auth');
  });

  it('命名空间出现在输出里', () => {
    const lines = capture();
    createLogger('app', plain).child('auth').info('ok');
    expect(lines[0].text).toContain('app › auth');
  });
});

describe('上下文', () => {
  it('withContext 追加 k=v 且不影响父实例', () => {
    const base = createLogger('t', plain);
    const withTrace = base.withContext({ traceId: 'abc', retry: 2 });
    const lines = capture();

    withTrace.info('a');
    base.info('b');

    expect(lines[0].text).toContain('traceId=abc retry=2');
    expect(lines[1].text).not.toContain('traceId');
  });

  it('withContext 可叠加', () => {
    const log = createLogger('t', plain).withContext({ a: 1 }).withContext({ b: 2 });
    const lines = capture();

    log.info('x');

    expect(lines[0].text).toContain('a=1 b=2');
  });
});

describe('格式化输出', () => {
  it('时间戳格式真正生效', () => {
    const lines = capture();
    createLogger('t', {
      showNamespace: false,
      showLevel: false,
      colorize: false,
      timestampFormat: 'HH:mm:ss',
    }).info('x');

    expect(lines[0].text).toMatch(/^\d{2}:\d{2}:\d{2} x$/);
  });

  it('showTimestamp / showNamespace / showLevel 可独立关闭', () => {
    const lines = capture();
    createLogger('t', {
      showTimestamp: false,
      showNamespace: false,
      showLevel: false,
      colorize: false,
    }).info('only message');

    expect(lines[0].text).toBe('only message');
  });

  it('不设置时间戳时默认展示毫秒', () => {
    const lines = capture();
    createLogger('t', { showNamespace: false, showLevel: false, colorize: false }).info('x');

    expect(lines[0].text).toMatch(/^\d{2}:\d{2}:\d{2}\.\d{3} x$/);
  });

  it('css 模式输出 %c 格式串 + 样式参数', () => {
    const lines = capture();
    createLogger('t', { showTimestamp: false }).info('hello');

    expect(lines[0].args.length).toBeGreaterThan(1);
    expect(lines[0].text).toContain('%c');
    expect(lines[0].text).toContain('hello');
    expect(String(lines[0].args[1])).toContain('color:');
  });

  it('plain 模式下只有文本一个参数，不残留样式串', () => {
    const lines = capture();
    createLogger('t', plain).info('hello');

    expect(lines[0].args).toHaveLength(1);
    expect(String(lines[0].args[0])).toContain('hello');
  });

  it('消息里的 %o / %c 被隔断，不会吞掉后续参数', () => {
    const lines = capture();
    const payload = { a: 1 };
    createLogger('t', plain).info('折扣 50%off', payload);

    const text = lines[0].text;
    expect(text).not.toContain('%o');
    expect(stripFormatGuard(text)).toContain('50%off');
    // 数据参数仍然完整透传
    expect(lines[0].args).toContain(payload);
  });
});

describe('数据与错误', () => {
  it('附加数据按原样透传，保持可在 DevTools 展开', () => {
    const lines = capture();
    const payload = { userId: 1 };
    createLogger('t', plain).info('ok', payload);

    expect(lines[0].args).toContain(payload);
  });

  it('error(Error) 自动带上错误对象以保留堆栈', () => {
    const lines = capture();
    const error = new Error('boom');
    createLogger('t', plain).error(error);

    expect(lines[0].method).toBe('error');
    expect(lines[0].text).toContain('boom');
    expect(lines[0].args).toContain(error);
  });

  it('error(string) 走普通路径', () => {
    const lines = capture();
    createLogger('t', plain).error('boom');
    expect(lines[0].text).toContain('boom');
    expect(lines[0].args).toHaveLength(1);
  });
});

describe('配置', () => {
  it('configure 热更新已创建实例', () => {
    const log = createLogger('t', plain);
    const lines = capture();

    log.info('before');
    log.configure({ level: 'error' });
    log.info('after');

    expect(lines).toHaveLength(1);
  });

  it('全局配置对"先创建后配置"的实例同样生效', () => {
    const log = createLogger('t');
    configureLogger({ ...plain, level: 'error' });
    const lines = capture();

    log.info('x');

    expect(lines).toHaveLength(0);
  });

  it('全局 sink 可全局接管输出', () => {
    const lines = capture();
    rootLogger.info('hi');
    expect(lines).toHaveLength(1);
  });

  it('sink 不设置时回落到真实 console', () => {
    resetLogger();
    const calls: string[] = [];
    const original = console.log;
    console.log = (...args: unknown[]) => {
      calls.push(String(args[0]));
    };
    try {
      createLogger('t', plain).info('real');
    } finally {
      console.log = original;
    }
    expect(calls[0]).toContain('real');
  });

  it('实例级 sink 优先于全局 sink', () => {
    const global = capture();
    const local: CapturedLine[] = [];
    createLogger('t', {
      ...plain,
      sink: (method, args) => local.push({ method, args, text: String(args[0]) }),
    }).info('x');

    expect(local).toHaveLength(1);
    expect(global).toHaveLength(0);
  });

  it('主题深合并生效（只覆盖 fg 不清掉 bg）', () => {
    const lines = capture();
    createLogger('t', {
      showTimestamp: false,
      theme: { colors: { info: { fg: '#ff0000' } } },
    }).info('x');

    const styles = lines[0].args.slice(1).join('|');
    expect(styles).toContain('#ff0000');
    expect(styles).toContain('#0c4a6e');
  });
});

describe('计时', () => {
  it('time() 返回停止函数并打印耗时', () => {
    const log = createLogger('t', plain);
    const lines = capture();

    const stop = log.time('load');
    const elapsed = stop();

    expect(typeof elapsed).toBe('number');
    expect(lines[0].text).toContain('load:');
    expect(lines[0].text).toMatch(/ms$/);
  });
});

describe('展示型输出', () => {
  it('table 超出行数上限时截断并提示', () => {
    const log = createLogger('t', { ...plain, maxTableRows: 2 });
    const lines = capture();

    log.table([{ a: 1 }, { a: 2 }, { a: 3 }]);

    expect(lines.some((line) => line.method === 'warn')).toBe(true);
    const tableCall = lines.find((line) => line.method === 'table');
    expect((tableCall?.args[0] as unknown[]).length).toBe(2);
  });

  it('table 支持列筛选', () => {
    const log = createLogger('t', plain);
    const lines = capture();

    log.table([{ a: 1, b: 2 }], ['a']);

    expect(lines[0].args[1]).toEqual(['a']);
  });

  it('group 输出折叠分组并配对闭合', () => {
    const log = createLogger('t', plain);
    const lines = capture();

    log.group('模块', () => {
      log.info('inside');
    });

    expect(lines.map((line) => line.method)).toEqual(['groupCollapsed', 'log', 'groupEnd']);
  });

  it('enableGrouping 关闭时退化为普通行，不丢输出', () => {
    const log = createLogger('t', { ...plain, enableGrouping: false });
    const lines = capture();

    log.group('模块', () => {
      log.info('inside');
    });

    expect(lines.map((line) => line.method)).toEqual(['log', 'log']);
  });

  it('banner 默认不清屏', () => {
    const log = createLogger('t', plain);
    const lines = capture();

    log.banner({ title: 'BlueWhale', version: '0.1.0' });

    expect(lines.some((line) => line.method === 'clear')).toBe(false);
    expect(lines.map((line) => line.text).join('\n')).toContain('BlueWhale');
    expect(lines.map((line) => line.text).join('\n')).toContain('v0.1.0');
  });

  it('banner 可选清屏', () => {
    const log = createLogger('t', plain);
    const lines = capture();

    log.banner({ title: 'BlueWhale', clear: true });

    expect(lines[0].method).toBe('clear');
  });

  it('keyValues 输出多行键值', () => {
    const log = createLogger('t', plain);
    const lines = capture();

    log.keyValues({ name: 'bluewhale', version: 1 });

    expect(lines).toHaveLength(2);
    expect(lines[0].text).toContain('name: bluewhale');
    expect(lines[1].text).toContain('version: 1');
  });

  it('section / divider 正常输出', () => {
    const log = createLogger('t', plain);
    const lines = capture();

    log.section('tech stack');
    log.divider('-', 10);

    expect(lines[0].text).toContain('TECH STACK');
    expect(lines[1].text).toBe('-'.repeat(10));
  });
});
