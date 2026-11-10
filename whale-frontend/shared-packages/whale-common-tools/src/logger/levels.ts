import type { ConsoleMethod, LogLevel, LogLevelThreshold } from './types';

/** 全部级别，按严重度升序。 */
export const LOG_LEVELS: readonly LogLevel[] = [
  'trace',
  'debug',
  'perf',
  'info',
  'success',
  'warn',
  'error',
];

/**
 * 级别的严重度数值，用于阈值比较。
 *
 * <p>`perf` 刻意排在 `info` 之上、`warn` 之下：计时是调用方的显式动作，
 * 默认应当可见；阈值提到 `warn` 时它也应一起安静下来。</p>
 */
export const LEVEL_SEVERITY: Readonly<Record<LogLevel, number>> = {
  trace: 10,
  debug: 20,
  info: 30,
  perf: 35,
  success: 30,
  warn: 40,
  error: 50,
};

/** 级别标签。 */
export const LEVEL_LABEL: Readonly<Record<LogLevel, string>> = {
  trace: 'TRACE',
  debug: 'DEBUG',
  perf: 'PERF',
  info: 'INFO',
  success: 'SUCCESS',
  warn: 'WARN',
  error: 'ERROR',
};

/** 标签统一宽度（取最长标签 SUCCESS 的长度），保证多行日志列对齐。 */
export const LEVEL_LABEL_WIDTH = 7;

/**
 * 级别 → console 方法。
 *
 * <p>warn → warn、error → error，trace / debug / perf → debug，
 * 以恢复 DevTools 的级别筛选与错误堆栈定位能力。</p>
 */
export const LEVEL_CONSOLE_METHOD: Readonly<Record<LogLevel, ConsoleMethod>> = {
  trace: 'debug',
  debug: 'debug',
  perf: 'debug',
  info: 'log',
  success: 'log',
  warn: 'warn',
  error: 'error',
};

/** 取级别的严重度数值。 */
export function severityOf(level: LogLevel): number {
  return LEVEL_SEVERITY[level];
}

/** 判断某级别是否达到输出阈值。 */
export function isLevelEnabled(level: LogLevel, threshold: LogLevelThreshold): boolean {
  if (threshold === 'silent') return false;
  return LEVEL_SEVERITY[level] >= LEVEL_SEVERITY[threshold];
}

/** 运行时类型守卫：判断任意值是否为合法级别。 */
export function isLogLevel(value: unknown): value is LogLevel {
  return typeof value === 'string' && (LOG_LEVELS as readonly string[]).includes(value);
}
