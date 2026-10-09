import { resolveDefaultLevel } from '../env';
import {
  detectRenderMode,
  escapeFormatTokens,
  formatTimestamp,
  paintAnsi,
  toCssStyle,
} from './format';
import { LEVEL_CONSOLE_METHOD, LEVEL_LABEL, LEVEL_LABEL_WIDTH, isLevelEnabled } from './levels';
import { BANNER_GRADIENT, resolveTheme } from './themes';
import type {
  BannerConfig,
  ConsoleMethod,
  CreditLink,
  KeyValueOptions,
  LogLevel,
  LogRecord,
  LogSegment,
  LogSink,
  LoggerConfig,
  RenderMode,
  ResolvedLoggerConfig,
  StyleMap,
} from './types';

/**
 * 默认输出通道：直写真实 console。
 *
 * 用动态取值而非 `console.log` 硬编码，是为了让 trace / debug / perf 落到
 * `console.debug`、warn 落到 `console.warn`、error 落到 `console.error`，
 * 从而恢复 DevTools 的级别筛选与错误堆栈定位。
 */
const DEFAULT_SINK: LogSink = (method, args) => {
  const target = console as unknown as Record<string, ((...rest: unknown[]) => void) | undefined>;
  const print = target[method];
  if (typeof print === 'function') print.apply(console, args);
};

let globalConfig: LoggerConfig = {};
let globalSink: LogSink | undefined;

/**
 * 配置版本号。
 *
 * 每次全局配置变化就自增，已创建的 Logger 检测到版本变化后重新解析配置 ——
 * 这样"模块顶层先创建 logger、入口里再 configure"这种常见顺序也能生效。
 */
let configVersion = 0;

/** 只保留显式赋值的字段，避免 `{...a, ...b}` 里 b 的 undefined 覆盖 a。 */
function stripUndefined<T extends object>(input: T | undefined): Partial<T> {
  if (!input) return {};
  const result: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(input)) {
    if (value !== undefined) result[key] = value;
  }
  return result as Partial<T>;
}

/** 把任意值压成一行短文本（对象转 JSON）。 */
function stringify(value: unknown): string {
  if (value === null) return 'null';
  if (value === undefined) return 'undefined';

  const type = typeof value;
  if (type === 'string') return value as string;
  if (type === 'number' || type === 'boolean' || type === 'bigint') return String(value);

  try {
    return JSON.stringify(value) ?? String(value);
  } catch {
    return String(value);
  }
}

/** 上下文对象 → `k=v k2=v2`。 */
function formatContext(context: Record<string, unknown>): string {
  return Object.entries(context)
    .map(([key, value]) => `${key}=${stringify(value)}`)
    .join(' ');
}

/** 高精度当前时间（毫秒）。 */
function now(): number {
  const perf = (globalThis as { performance?: { now?: () => number } }).performance;
  return typeof perf?.now === 'function' ? perf.now() : Date.now();
}

/** 保留两位小数，避免日志里出现 12.345678901 这类噪声。 */
function round(value: number): number {
  return Math.round(value * 100) / 100;
}

/**
 * 解析生效配置。
 *
 * 层次：内置默认 → 环境推导默认 → 全局配置 → 实例配置，
 * 每层只覆盖上一层「显式设置过」的字段。
 */
function resolveConfig(local: LoggerConfig | undefined): ResolvedLoggerConfig {
  const merged: LoggerConfig = {
    level: resolveDefaultLevel(),
    ...stripUndefined(globalConfig),
    ...stripUndefined(local),
  };

  const autoMode = detectRenderMode();
  const colorize = merged.colorize ?? autoMode !== 'plain';
  // 显式要求着色时，即使探测到管道（plain）也强制走 ANSI —— 尊重调用方的明确意图
  const renderMode: RenderMode = !colorize ? 'plain' : autoMode === 'plain' ? 'ansi' : autoMode;

  return {
    level: merged.level ?? 'info',
    showTimestamp: merged.showTimestamp ?? true,
    timestampFormat: merged.timestampFormat ?? 'HH:mm:ss.SSS',
    showNamespace: merged.showNamespace ?? true,
    showLevel: merged.showLevel ?? true,
    colorize,
    renderMode,
    theme: resolveTheme(merged.theme),
    namespaceSeparator: merged.namespaceSeparator ?? ' › ',
    context: merged.context,
    sink: merged.sink,
    onRecord: merged.onRecord,
    traceId: merged.traceId,
    sessionId: merged.sessionId,
    route: merged.route,
    release: merged.release,
    maxTableRows: merged.maxTableRows ?? 50,
    enableGrouping: merged.enableGrouping ?? true,
  };
}

/**
 * 模块级 Logger。
 *
 * 命名空间支持层级（`a › b › c`），每个实例可独立配置，
 * 也可通过 {@link Logger.child} 派生并继承父级配置。
 */
export class Logger {
  /** 命名空间，形如 `app › auth › login`。 */
  readonly namespace: string;

  private ownConfig: LoggerConfig;
  private resolved: ResolvedLoggerConfig | undefined;
  private resolvedVersion = -1;

  constructor(namespace = '', config?: LoggerConfig) {
    this.namespace = namespace;
    this.ownConfig = stripUndefined(config) as LoggerConfig;
  }

  /**
   * 生效配置（惰性解析）。
   *
   * 旧实现只在构造时读一次全局配置，于是 `configure()` 之后再创建的实例
   * 与已存在的单例行为不一致。这里改为按版本号失效重解析。
   */
  private get cfg(): ResolvedLoggerConfig {
    if (!this.resolved || this.resolvedVersion !== configVersion) {
      this.resolved = resolveConfig(this.ownConfig);
      this.resolvedVersion = configVersion;
    }
    return this.resolved;
  }

  // ==================== 派生与配置 ====================

  /** 热更新本实例配置。 */
  configure(patch: LoggerConfig): this {
    this.ownConfig = { ...this.ownConfig, ...stripUndefined(patch) };
    this.resolved = undefined;
    return this;
  }

  /** 派生子命名空间 logger，继承当前实例的全部配置。 */
  child(name: string, config?: LoggerConfig): Logger {
    const namespace = name
      ? [this.namespace, name].filter(Boolean).join(this.cfg.namespaceSeparator)
      : this.namespace;
    return new Logger(namespace, { ...this.ownConfig, ...stripUndefined(config) });
  }

  /** 派生出附带上下文的 logger（如 traceId），父子互不影响。 */
  withContext(context: Record<string, unknown>): Logger {
    return new Logger(this.namespace, {
      ...this.ownConfig,
      context: { ...this.cfg.context, ...context },
    });
  }

  /** 该级别在当前阈值下是否会输出 —— 用于跳过昂贵的参数构造。 */
  isEnabled(level: LogLevel): boolean {
    return isLevelEnabled(level, this.cfg.level);
  }

  /** 条件执行：级别未启用时连闭包都不执行。 */
  ifEnabled(level: LogLevel, body: () => void): void {
    if (this.isEnabled(level)) body();
  }

  // ==================== 级别方法 ====================

  trace(message: string, ...data: unknown[]): void {
    this.emit('trace', message, data);
  }

  debug(message: string, ...data: unknown[]): void {
    this.emit('debug', message, data);
  }

  info(message: string, ...data: unknown[]): void {
    this.emit('info', message, data);
  }

  success(message: string, ...data: unknown[]): void {
    this.emit('success', message, data);
  }

  warn(message: string, ...data: unknown[]): void {
    this.emit('warn', message, data);
  }

  /** 错误日志。直接传 Error 时会自动把错误对象作为附加数据带上，保留堆栈。 */
  error(message: string | Error, ...data: unknown[]): void {
    if (message instanceof Error) {
      this.emit('error', message.message, [message, ...data]);
      return;
    }
    this.emit('error', message, data);
  }

  /** 通用入口，便于按变量级别输出。 */
  log(level: LogLevel, message: string, ...data: unknown[]): void {
    this.emit(level, message, data);
  }

  /** 性能日志：显式给值。 */
  perf(label: string, value: number, unit = 'ms'): void {
    this.emit('perf', `${label}: ${value}${unit}`, []);
  }

  /**
   * 计时。返回停止函数，调用后打印耗时并回传毫秒数。
   *
   * 用返回值而非 `console.time(label)` 的全局标签池：后者在并发或同名嵌套时
   * 会互相覆盖，且未匹配时静默失败。
   */
  time(label: string): () => number {
    const start = now();
    return () => {
      const elapsed = now() - start;
      this.perf(label, round(elapsed));
      return elapsed;
    };
  }

  // ==================== 展示型输出 ====================

  /** 小标题。 */
  section(title: string, icon = '◆'): void {
    this.output('log', [
      {
        text: `${icon} ${title.toUpperCase()}`,
        style: { color: this.cfg.theme.palette.accent, 'font-weight': '700' },
      },
    ]);
  }

  /** 分隔线。 */
  divider(char = '─', length = 60): void {
    this.output('log', [
      { text: char.repeat(Math.max(0, length)), style: { color: this.cfg.theme.palette.divider } },
    ]);
  }

  /** 单行键值对。 */
  keyValue(key: string, value: unknown, options?: KeyValueOptions): void {
    this.keyValues({ [key]: value }, options);
  }

  /** 多行键值对。 */
  keyValues(entries: Record<string, unknown>, options: KeyValueOptions = {}): void {
    const { palette } = this.cfg.theme;
    const keyColor = options.keyColor ?? palette.muted;
    const valueColor = options.valueColor ?? palette.accent;
    const indent = ' '.repeat(Math.max(0, options.indent ?? 2));

    for (const [key, value] of Object.entries(entries)) {
      this.output('log', [
        { text: `${indent}${key}: `, style: { color: keyColor } },
        { text: stringify(value), style: { color: valueColor, 'font-weight': '600' } },
      ]);
    }
  }

  /** 表格。超过 maxTableRows 时自动截断并提示，避免刷屏。 */
  table(rows: readonly Record<string, unknown>[], columns?: readonly string[]): void {
    const { maxTableRows } = this.cfg;
    let visible: readonly Record<string, unknown>[] = rows;

    if (maxTableRows > 0 && rows.length > maxTableRows) {
      this.warn(`表格共 ${rows.length} 行，按 maxTableRows=${maxTableRows} 截断显示`);
      visible = rows.slice(0, maxTableRows);
    }

    this.write('table', columns?.length ? [visible, [...columns]] : [visible]);
  }

  /** 折叠分组。`enableGrouping` 关闭时退化为普通行，不会丢失输出。 */
  group(label: string, body: () => void, icon = '▸'): void {
    const style: StyleMap = { color: this.cfg.theme.palette.accent, 'font-weight': '600' };

    if (!this.cfg.enableGrouping) {
      this.output('log', [{ text: `${icon} ${label}`, style }]);
      body();
      return;
    }

    this.output('groupCollapsed', [{ text: `${icon} ${label}`, style }]);
    try {
      body();
    } finally {
      this.write('groupEnd', []);
    }
  }

  /** 展开分组。 */
  groupOpen(label: string, icon = '▾'): void {
    const style: StyleMap = { color: this.cfg.theme.palette.accent, 'font-weight': '600' };
    if (!this.cfg.enableGrouping) {
      this.output('log', [{ text: `${icon} ${label}`, style }]);
      return;
    }
    this.output('group', [{ text: `${icon} ${label}`, style }]);
  }

  /** 关闭分组。 */
  groupEnd(): void {
    this.write('groupEnd', []);
  }

  /**
   * 启动 Banner。
   *
   * 与旧实现的两处差异：
   * - 不再默认 `console.clear()`（会清掉控制台里别人的输出），需要时传 `clear: true`；
   * - 不再内部 fetch ASCII 文件（日志组件不该发网络请求），改为由调用方传入字符串。
   */
  banner(config: BannerConfig): void {
    const { palette } = this.cfg.theme;
    if (config.clear) this.clear();

    if (config.asciiArt) {
      for (const [index, line] of config.asciiArt.split('\n').entries()) {
        if (!line.trim()) {
          this.output('log', [{ text: '', style: {} }]);
          continue;
        }
        this.output('log', [
          {
            text: line,
            style: {
              color: BANNER_GRADIENT[index % BANNER_GRADIENT.length],
              'font-weight': '700',
            },
          },
        ]);
      }
    } else {
      this.output('log', [
        { text: `✨ ${config.title}`, style: { color: palette.accent, 'font-weight': '700' } },
      ]);
    }

    if (config.subtitle || config.version) {
      this.output('log', [
        { text: `${config.subtitle ?? ''} `, style: { color: palette.muted } },
        { text: `v${config.version ?? '0.0.0'}`, style: { color: palette.accent } },
      ]);
    }

    if (config.githubUrl) {
      this.output('log', [
        {
          text: `  ${config.githubUrl}`,
          style: { color: palette.link, 'text-decoration': 'underline' },
        },
      ]);
    }
  }

  /** 链接列表。 */
  credits(links: readonly CreditLink[]): void {
    const { palette } = this.cfg.theme;
    this.section('Links', '🔗');
    for (const { label, url } of links) {
      this.output('log', [
        { text: `  ${label}: `, style: { color: palette.muted } },
        { text: url, style: { color: palette.link, 'text-decoration': 'underline' } },
      ]);
    }
  }

  /** 清屏。 */
  clear(): void {
    this.write('clear', []);
  }

  // ==================== 内部实现 ====================

  private emit(level: LogLevel, message: string, data: unknown[]): void {
    const cfg = this.cfg;
    // 过滤前置于格式化：被压制的日志不产生任何字符串拼接开销
    if (!isLevelEnabled(level, cfg.level)) return;

    // 先派发结构化记录、再渲染：上报消费「事实」，控制台消费「样子」。
    // 顺序不可颠倒 —— 即便渲染环节抛错，记录也已经交出去了。
    this.dispatch(level, message, data, cfg);

    this.output(LEVEL_CONSOLE_METHOD[level], this.segments(level, message), data);
  }

  /** 构造并派发结构化记录（未设置 onRecord 时零开销）。 */
  private dispatch(
    level: LogLevel,
    message: string,
    data: unknown[],
    cfg: ResolvedLoggerConfig,
  ): void {
    const handler = cfg.onRecord;
    if (!handler) return;

    const context = cfg.context;
    const record: LogRecord = {
      level,
      namespace: this.namespace,
      timestamp: Date.now(),
      message,
      data,
      context: context && Object.keys(context).length > 0 ? { ...context } : undefined,
      traceId: cfg.traceId,
      sessionId: cfg.sessionId,
      route: cfg.route,
      release: cfg.release,
    };

    try {
      handler(record);
    } catch {
      // 上报通道自身的异常必须静默吞掉：否则「上报失败」会再产生一条日志、
      // 又触发一次上报，形成雪崩。日志系统的故障绝不允许反噬业务。
    }
  }

  /** 把一行日志拆成带样式的片段。 */
  private segments(level: LogLevel, message: string): LogSegment[] {
    const cfg = this.cfg;
    const { colors, palette } = cfg.theme;
    const parts: LogSegment[] = [];

    if (cfg.showTimestamp) {
      parts.push({
        text: formatTimestamp(new Date(), cfg.timestampFormat),
        style: { color: palette.timestamp },
      });
    }

    if (cfg.showNamespace && this.namespace) {
      parts.push({
        text: this.namespace,
        style: { color: palette.namespace, 'font-weight': '600' },
      });
    }

    if (cfg.showLevel) {
      parts.push({
        text: LEVEL_LABEL[level].padEnd(LEVEL_LABEL_WIDTH),
        style: { color: colors[level].fg, background: colors[level].bg, 'font-weight': '700' },
      });
    }

    parts.push({ text: message, style: { color: palette.message } });

    if (cfg.context && Object.keys(cfg.context).length > 0) {
      parts.push({ text: formatContext(cfg.context), style: { color: palette.muted } });
    }

    // 间隔统一由「后一个片段」携带前导空格，这样每段间隔跟着自己的样式走，
    // 级别标签的底色才会连成一个完整的色块。
    return parts.map((part, index) => (index === 0 ? part : { ...part, text: ` ${part.text}` }));
  }

  /**
   * 按渲染模式输出片段。
   *
   * 约定：片段文本按原样拼接，片段之间的间隔由调用方写在文本里。
   */
  private output(method: ConsoleMethod, segments: LogSegment[], trailing: unknown[] = []): void {
    const { renderMode } = this.cfg;

    if (renderMode === 'css') {
      let format = '';
      const styles: string[] = [];
      for (const segment of segments) {
        format += `%c${escapeFormatTokens(segment.text)}`;
        styles.push(toCssStyle(segment.style));
      }
      this.write(method, [format, ...styles, ...trailing]);
      return;
    }

    if (renderMode === 'ansi') {
      const line = segments
        .map((segment) => paintAnsi(escapeFormatTokens(segment.text), segment.style))
        .join('');
      this.write(method, [line, ...trailing]);
      return;
    }

    const line = segments.map((segment) => escapeFormatTokens(segment.text)).join('');
    this.write(method, [line, ...trailing]);
  }

  private write(method: ConsoleMethod, args: unknown[]): void {
    const sink = this.cfg.sink ?? globalSink ?? DEFAULT_SINK;
    sink(method, args);
  }
}

/** 创建 Logger 实例。 */
export function createLogger(namespace: string, config?: LoggerConfig): Logger {
  return new Logger(namespace, config);
}

/** 设置全局配置，影响所有 Logger（含已创建的实例）。 */
export function configureLogger(config: LoggerConfig): void {
  globalConfig = { ...globalConfig, ...stripUndefined(config) };
  configVersion += 1;
}

/** 读取当前全局配置（副本）。 */
export function getLoggerConfig(): LoggerConfig {
  return { ...globalConfig };
}

/** 替换全局输出通道；不传则恢复默认 console。 */
export function setLoggerSink(sink?: LogSink): void {
  globalSink = sink;
  configVersion += 1;
}

/** 读取当前全局输出通道。 */
export function getLoggerSink(): LogSink | undefined {
  return globalSink;
}

/** 复位全局配置与全局 sink（测试与热更新场景使用）。 */
export function resetLogger(): void {
  globalConfig = {};
  globalSink = undefined;
  configVersion += 1;
}

/** 默认根 logger，命名空间为 `app`。 */
export const logger = new Logger('app');
